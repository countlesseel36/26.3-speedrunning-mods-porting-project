package me.contaria.speedrunapi.config.api.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD})
public @interface Config {
   InitPoint init() default InitPoint.ONINITIALIZE;

   int priority() default 1000;

   @Retention(RetentionPolicy.RUNTIME)
   @Target(ElementType.FIELD)
   @interface Category {
      String value();
   }

   @Retention(RetentionPolicy.RUNTIME)
   @Target(ElementType.FIELD)
   @interface Name {
      String value();
   }

   @Retention(RetentionPolicy.RUNTIME)
   @Target(ElementType.FIELD)
   @interface Description {
      String value();
   }

   @Retention(RetentionPolicy.RUNTIME)
   @Target(ElementType.FIELD)
   @interface Ignored {
   }

   @Retention(RetentionPolicy.RUNTIME)
   @Target(ElementType.FIELD)
   @interface Text {
      String value();
   }

   @Retention(RetentionPolicy.RUNTIME)
   @Target(ElementType.FIELD)
   @interface Hide {
   }

   @Retention(RetentionPolicy.RUNTIME)
   @Target(ElementType.FIELD)
   @interface Access {
      String value() default "";
   }

   enum InitPoint {
      PRELAUNCH,
      ONINITIALIZE,
      POSTLAUNCH
   }

   @interface Numbers {
      @interface Whole {
         @Retention(RetentionPolicy.RUNTIME)
         @Target(ElementType.FIELD)
         @interface Bounds {
            long min() default Long.MIN_VALUE;

            long max() default Long.MAX_VALUE;
         }

         @Retention(RetentionPolicy.RUNTIME)
         @Target(ElementType.FIELD)
         @interface Intervals {
            long value() default 1L;
         }

         @Retention(RetentionPolicy.RUNTIME)
         @Target(ElementType.FIELD)
         @interface TextField {
         }
      }

      @interface Fractional {
         @Retention(RetentionPolicy.RUNTIME)
         @Target(ElementType.FIELD)
         @interface Bounds {
            double min() default -Double.MAX_VALUE;

            double max() default Double.MAX_VALUE;
         }

         @Retention(RetentionPolicy.RUNTIME)
         @Target(ElementType.FIELD)
         @interface Intervals {
            double value() default 0.1;
         }

         @Retention(RetentionPolicy.RUNTIME)
         @Target(ElementType.FIELD)
         @interface TextField {
         }
      }

      @Retention(RetentionPolicy.RUNTIME)
      @Target(ElementType.FIELD)
      @interface EnforceBounds {
      }
   }

   @interface Strings {
      @Retention(RetentionPolicy.RUNTIME)
      @Target(ElementType.FIELD)
      @interface MaxChars {
         int value() default 32;
      }
   }
}
