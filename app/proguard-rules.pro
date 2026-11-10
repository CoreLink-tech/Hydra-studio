# FRAME ProGuard rules (Milestone 1 — minimal)
# Keep Hilt / Room generated code
-keep class * extends androidx.room.RoomDatabase
-dontwarn com.google.dagger.**
