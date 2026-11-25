# Voice AI (Android)

বাংলা ভয়েস AI অ্যাসিস্ট্যান্ট। ফ্রি Gemini API key অ্যাপের ⚙ Settings এ বসাতে হবে (key কোডে নেই)।

## Termux দিয়ে GitHub এ পুশ
    pkg install git unzip -y
    termux-setup-storage
    cd ~ && unzip /sdcard/Download/VoiceAI-android.zip && cd VoiceAI
    git init && git add . && git commit -m "init"
    git branch -M main
    git remote add origin https://github.com/USERNAME/REPO.git
    git push -u origin main    # password এর জায়গায় GitHub Personal Access Token

## APK পাওয়া
GitHub repo → Actions → Build APK → সবুজ হলে নিচে Artifacts থেকে VoiceAI-apk ডাউনলোড (জিপের ভেতরে app-debug.apk) → ফোনে ইনস্টল।

## ব্যবহার
1. aistudio.google.com এ ফ্রি API key নিন। 2. অ্যাপে ⚙ → key বসান → Save। 3. 🎤 চাপুন, কথা বলুন। TERMINATE চাপলে বন্ধ।
বাংলা কণ্ঠ না এলে ফোনের Settings → Text-to-speech এ Google এর Bengali voice ডাউনলোড করুন।
