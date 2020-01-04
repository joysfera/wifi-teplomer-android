# WiFi Teploměr pro Android

This is an application for Android that supports my [WiFi thermometer project](https://teploty.info/ "Teploty.info").

It's incomplete and unpolished, hacked together from various examples and generally below my normal coding standards.

I'm releasing it as-is with the hope that interested parties could provide pull requests with improvements.

## Credentials
If you want to test it out you can use login `demo` and empty password.

## Widget
Creating an Android widget that works in the background and refreshes automagically its content by downloading data from internet turned out to be a daunting task because nowadays various Android OEM flavors like to kill every application that does not run in foreground, which unfortunately includes also all widgets. Please see the web [Don't kill my app!](https://dontkillmyapp.com/) to see how bad the situation for programmers and users is. I tried working around this issue on some phones by various hacks in the source code - git log can show you what was tried and what helped or didn't help. Every Android version and every OEM modified Android is different, unfortunately. Getting the widget working reliably is a neverending fight.

Enjoy

petr@pstehlik.cz
