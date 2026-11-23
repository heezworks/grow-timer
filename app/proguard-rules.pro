# kotlinx.serialization: @Serializable 클래스의 serializer를 남긴다
-keepclassmembers @kotlinx.serialization.Serializable class ** { *** Companion; }
-keepclasseswithmembers class ** { kotlinx.serialization.KSerializer serializer(...); }
