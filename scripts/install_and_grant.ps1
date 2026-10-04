$apkPath = "app\build\outputs\apk\debug\app-debug.apk"
$pkg = "com.example.turnaway"
$accessibilityService = "com.example.turnaway/com.example.turnaway.service.SoftLandingAccessibilityService"

Write-Host "Waiting for device to connect with USB debugging enabled..."

$deviceFound = $false
for ($i = 0; $i -lt 120; $i++) {
    $devices = (adb devices | Select-String -Pattern "^\s*([^\s]+)\s+device\b")
    if ($devices) {
        $deviceFound = $true
        break
    }
    Start-Sleep -Seconds 2
}

if (-not $deviceFound) {
    Write-Warning "No authorized device detected yet. Please ensure USB debugging is enabled on the device."
    exit 1
}

Write-Host "Device detected! Installing TurnAway APK ($apkPath)..."
adb install -r -d $apkPath

Write-Host "Granting permissions..."
adb shell pm grant $pkg android.permission.WRITE_SECURE_SETTINGS
adb shell pm grant $pkg android.permission.POST_NOTIFICATIONS
adb shell appops set $pkg SYSTEM_ALERT_WINDOW allow

Write-Host "Enabling Soft-Landing Accessibility Service..."
$currentServices = (adb shell settings get secure enabled_accessibility_services)
if ($null -ne $currentServices) {
    $currentServices = $currentServices.Trim()
}
if ($currentServices -eq "null" -or -not $currentServices) {
    adb shell settings put secure enabled_accessibility_services $accessibilityService
} elseif (-not $currentServices.Contains($accessibilityService)) {
    $merged = $currentServices + ":" + $accessibilityService
    adb shell settings put secure enabled_accessibility_services $merged
}
adb shell settings put secure accessibility_enabled 1

Write-Host "Launching TurnAway..."
adb shell am start -n "$pkg/.MainActivity"

Write-Host "Done! TurnAway installed and all permissions successfully granted."
