import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Static sanity check for the 26.3 mixins: verifies that every @Mixin target, every injector
 * target method and every INVOKE injection point actually exists in the vanilla jar.
 * This is not a replacement for launching the game, but it catches the usual porting mistakes.
 */
public final class CheckMixins {
   private static ZipFile vanillaJar;
   private static int failures;

   public static void main(String[] args) throws Exception {
      if (args.length < 2) {
         System.err.println("usage: CheckMixins <minecraft.jar> <classes-dir>...");
         System.exit(2);
      }

      vanillaJar = new ZipFile(args[0]);
      List<Path> classFiles = new ArrayList<>();
      for (int i = 1; i < args.length; i++) {
         try (Stream<Path> stream = Files.walk(Paths.get(args[i]))) {
            stream.filter(p -> p.toString().endsWith(".class")).forEach(classFiles::add);
         }
      }

      for (Path file : classFiles) {
         checkMixin(file);
      }

      vanillaJar.close();
      System.out.println(failures == 0 ? "ALL MIXIN TARGETS OK" : failures + " PROBLEM(S) FOUND");
      System.exit(failures == 0 ? 0 : 1);
   }

   private static void checkMixin(Path file) throws IOException {
      ClassNode node = readClass(Files.readAllBytes(file));
      AnnotationNode mixin = findAnnotation(node, "Lorg/spongepowered/asm/mixin/Mixin;");
      if (mixin == null) {
         return;
      }

      List<String> targets = new ArrayList<>();
      Object value = get(mixin, "value");
      Object targetsValue = get(mixin, "targets");

      if (value instanceof List<?> list) {
         for (Object entry : list) {
            targets.add(((org.objectweb.asm.Type) entry).getInternalName());
         }
      } else if (value instanceof org.objectweb.asm.Type type) {
         targets.add(type.getInternalName());
      }

      if (targetsValue instanceof List<?> list) {
         for (Object entry : list) {
            targets.add(((String) entry).replace('.', '/'));
         }
      } else if (targetsValue instanceof String s) {
         targets.add(s.replace('.', '/'));
      }

      for (String target : targets) {
         ClassNode targetNode = readVanilla(target);
         if (targetNode == null) {
            fail(file, "mixin target class not found: " + target);
            continue;
         }

         for (MethodNode method : node.methods) {
            AnnotationNode inject = findAnnotation(method, "Lorg/spongepowered/asm/mixin/injection/Inject;");
            AnnotationNode redirect = findAnnotation(method, "Lorg/spongepowered/asm/mixin/injection/Redirect;");
            AnnotationNode modifyArg = findAnnotation(method, "Lorg/spongepowered/asm/mixin/injection/ModifyArg;");
            AnnotationNode invoker = findAnnotation(method, "Lorg/spongepowered/asm/mixin/gen/Invoker;");

            if (invoker != null) {
               String name = (String) get(invoker, "value");
               if (findMethods(targetNode, name, null).isEmpty()) {
                  fail(file, "@Invoker target not found: " + target + "#" + name);
               }
            }

            AnnotationNode annotation = inject != null ? inject : redirect != null ? redirect : modifyArg;
            if (annotation == null) {
               continue;
            }

            String kind = inject != null ? "@Inject" : redirect != null ? "@Redirect" : "@ModifyArg";
            for (String spec : methodSpecs(annotation)) {
               List<MethodNode> matched = findMethods(targetNode, nameOf(spec), descriptorOf(spec));
               if (matched.isEmpty()) {
                  fail(file, kind + " target method not found: " + target + "#" + spec);
                  continue;
               }

               Object atValueObject = get(annotation, "at");
               if (atValueObject instanceof List<?> atList) {
                  atValueObject = atList.isEmpty() ? null : atList.get(0);
               }
               AnnotationNode at = (AnnotationNode) atValueObject;
               if (at == null) {
                  continue;
               }

               String atValue = (String) get(at, "value");
               String atTarget = (String) get(at, "target");
               if (!"INVOKE".equals(atValue) || atTarget == null) {
                  continue;
               }

               MemberRef ref = parseMemberRef(atTarget);
               if (ref == null) {
                  fail(file, "cannot parse injection target: " + atTarget);
                  continue;
               }

               ClassNode ownerNode = readVanilla(ref.owner);
               if (ownerNode == null) {
                  if (!ref.owner.startsWith("java/")) {
                     fail(file, "injection owner class not found: " + ref.owner);
                     continue;
                  }
               } else if (ref.isMethod && findMethods(ownerNode, ref.name, ref.desc).isEmpty()) {
                  fail(file, "injected member does not exist: " + ref.owner + "#" + ref.name + ref.desc);
                  continue;
               }

               for (MethodNode matchedMethod : matched) {
                  int count = countInvocations(matchedMethod, ref);
                  if (count == 0) {
                     fail(file, kind + " point not present in " + target + "#" + spec + ": " + atTarget);
                  } else if (redirect != null && count > 1) {
                     fail(file, "@Redirect point is ambiguous (" + count + "x) in " + target + "#" + spec + ": " + atTarget);
                  }
               }
            }
         }
      }
   }

   private static List<String> methodSpecs(AnnotationNode annotation) {
      List<String> result = new ArrayList<>();
      Object value = get(annotation, "method");
      if (value instanceof List<?> list) {
         for (Object entry : list) {
            result.add((String) entry);
         }
      } else if (value instanceof String s) {
         result.add(s);
      }
      return result;
   }

   private static String nameOf(String spec) {
      int paren = spec.indexOf('(');
      return paren < 0 ? spec : spec.substring(0, paren);
   }

   private static String descriptorOf(String spec) {
      int paren = spec.indexOf('(');
      return paren < 0 ? null : spec.substring(paren);
   }

   private static List<MethodNode> findMethods(ClassNode node, String name, String descriptor) {
      List<MethodNode> result = new ArrayList<>();
      for (MethodNode method : node.methods) {
         if (method.name.equals(name) && (descriptor == null || method.desc.equals(descriptor))) {
            result.add(method);
         }
      }
      return result;
   }

   private static int countInvocations(MethodNode method, MemberRef ref) {
      int count = 0;
      for (AbstractInsnNode instruction : method.instructions) {
         if (instruction.getType() == AbstractInsnNode.METHOD_INSN) {
            MethodInsnNode call = (MethodInsnNode) instruction;
            if (call.owner.equals(ref.owner) && call.name.equals(ref.name) && call.desc.equals(ref.desc)) {
               count++;
            }
         }
      }
      return count;
   }

   private record MemberRef(String owner, String name, String desc, boolean isMethod) {
   }

   private static MemberRef parseMemberRef(String target) {
      // Lowner;name(desc)ret  or  Lowner;field:Type
      if (!target.startsWith("L")) {
         return null;
      }
      int semi = target.indexOf(';');
      if (semi < 0) {
         return null;
      }
      String owner = target.substring(1, semi);
      String rest = target.substring(semi + 1);
      int paren = rest.indexOf('(');
      if (paren >= 0) {
         String name = rest.substring(0, paren);
         String desc = rest.substring(paren);
         return new MemberRef(owner, name, desc, true);
      }
      int colon = rest.indexOf(':');
      if (colon >= 0) {
         return new MemberRef(owner, rest.substring(0, colon), rest.substring(colon + 1), false);
      }
      return null;
   }

   private static ClassNode readVanilla(String internalName) throws IOException {
      ZipEntry entry = vanillaJar.getEntry(internalName + ".class");
      if (entry == null) {
         return null;
      }
      try (InputStream in = vanillaJar.getInputStream(entry)) {
         return readClass(in.readAllBytes());
      }
   }

   private static ClassNode readClass(byte[] bytes) {
      ClassNode node = new ClassNode();
      new ClassReader(bytes).accept(node, 0);
      return node;
   }

   private static AnnotationNode findAnnotation(ClassNode node, String desc) {
      return find(node.invisibleAnnotations, node.visibleAnnotations, desc);
   }

   private static AnnotationNode findAnnotation(MethodNode node, String desc) {
      return find(node.invisibleAnnotations, node.visibleAnnotations, desc);
   }

   private static AnnotationNode find(List<AnnotationNode> invisible, List<AnnotationNode> visible, String desc) {
      if (invisible != null) {
         for (AnnotationNode annotation : invisible) {
            if (annotation.desc.equals(desc)) {
               return annotation;
            }
         }
      }
      if (visible != null) {
         for (AnnotationNode annotation : visible) {
            if (annotation.desc.equals(desc)) {
               return annotation;
            }
         }
      }
      return null;
   }

   private static Object get(AnnotationNode annotation, String key) {
      if (annotation.values == null) {
         return null;
      }
      for (int i = 0; i < annotation.values.size(); i += 2) {
         if (annotation.values.get(i).equals(key)) {
            return annotation.values.get(i + 1);
         }
      }
      return null;
   }

   private static void fail(Path file, String message) {
      failures++;
      System.out.println("FAIL " + file.getFileName() + ": " + message);
   }
}
