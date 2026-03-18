# Preserve enough stack trace information for crash symbolication.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep Kotlin metadata annotations consumed by reflection-based tooling.
-keep class kotlin.Metadata { *; }
