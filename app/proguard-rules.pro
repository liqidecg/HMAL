# Xposed
-keepclassmembers class com.android.lbe.MyApp {
    boolean isHooked;
}

# Enum class
-keepclassmembers,allowoptimization enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keep,allowoptimization class * extends androidx.preference.PreferenceFragmentCompat
-keepclassmembers class com.lbe.security.databinding.**  {
    public <methods>;
}
