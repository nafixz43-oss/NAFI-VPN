# NAFI VPN — Android starter project

নিজস্ব Dark Blue & Purple Neon UI-সহ WireGuard Android client starter project।

## কী আছে
- Kotlin + Jetpack Compose dashboard
- Login ছাড়াই ব্যবহার
- `.conf` WireGuard profile import
- একাধিক imported profile থেকে manual selection
- Automatic = প্রথম imported profile
- WireGuard Tunnel Library দিয়ে real VPN connect/disconnect
- GitHub Actions দিয়ে Debug APK build

## গুরুত্বপূর্ণ সীমাবদ্ধতা
এই project-এ কোনো public/free VPN server address বা credentials দেওয়া নেই। VPN চালাতে আপনার বেছে নেওয়া provider-এর বৈধ WireGuard `.conf` profile লাগবে। Provider যদি শুধু OpenVPN বা অন্য protocol দেয়, এই app-এ তা চলবে না। Automatic নির্বাচন বর্তমানে প্রথম imported profile বেছে নেয়; latency test বা server discovery করে না।

অজানা provider-এর configuration ব্যবহার করবেন না। `.conf` ফাইলে PrivateKey থাকে—এটি password-এর মতো গোপন রাখুন। এই app profile-গুলো app-এর private storage-এ রাখে; app uninstall করলে সেগুলো মুছে যাবে। Debug APK testing-এর জন্য; Play Store/release ব্যবহারের আগে নিজস্ব signing key, privacy policy, security review এবং Android policy review প্রয়োজন।

## GitHub থেকে ফোনে APK build
1. ZIP extract করে GitHub-এ নতুন repository বানিয়ে সব ফাইল upload করুন।
2. Repository-এর Actions tab খুলুন।
3. `Build NAFI VPN APK` workflow নির্বাচন করে `Run workflow` চাপুন।
4. Build সফল হলে workflow run-এর Artifacts থেকে `NAFI-VPN-debug` download করুন।
5. ZIP extract করে `app-debug.apk` Android ফোনে install করুন। Android-এর install unknown apps permission চাইতে পারে।

## Local build
Android Studio, JDK 17, Android SDK 35 এবং Gradle 8.9 ব্যবহার করুন:
`gradle assembleDebug`

## WireGuard dependency
`com.wireguard.android:tunnel:1.0.20260102` (Apache-2.0)। WireGuard upstream embedding documentation:
https://www.wireguard.com/embedding/
