# Android Wifi Checker

Improve Wifi connectivity in locations with multiple WLAN access points

## Description

Sometimes Android devices do not automatically switch to the strongest possible wireless network. This interrupts or slows connectivity.

Android Wifi Checker checks, whenever you switch on your Android device, whether the wifi signal strength is above an acceptable limit.
If not it quickly turns Wifi off and on again. This causes Android to pick the network with the strongest signal in your vicinity.
After unlocking your screen you will immediately be connected to the best possible wifi network.

Android Wifi Checker is free and open source software. It comes with no guarantees, use it on your own risk. For details please see the license below.

Author: Leo Meyer (meyer@software-services.de)

Based on: https://github.com/robertohuertasm/endless-service

## Usage

Install Android Studio, create a project by cloning this repository, connect your device (enable USB debugging first) and run the app.

Tap the "Start Wifi Checker" button to enable the service. Play with the settings if you like. The app can be closed without interrupting the service, however, after a reboot, you need to manually restart the app. If you figure out how to work around this please send a pull request.

## License

MIT License

Copyright (c) 2022 Leo Meyer (meyer@software-services.de)

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
