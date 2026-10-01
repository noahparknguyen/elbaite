# Security policy

## Reporting a vulnerability

Please report security problems privately: open the repository's **Security** tab and choose
**Report a vulnerability**. Please don't open a public issue for them.

Say what you found, which version and download it affects (the Windows installer or zip, the
Linux `.deb` or `.tar.gz`, or the jar), and how to reproduce it. I'll reply as soon as I can,
and credit you in the fix's release notes if you'd like.

## Supported versions

Only the latest release is fixed. Each package carries the Java runtime that was current when
it was built, and its release notes name it. The jar runs on your own Java 25 instead, which
stays as up to date as you keep it.

## What Achroite does on your computer

It reads the ROM file you open and nothing else. It writes no files of its own, opens no
network connections, runs no other programs, and keeps no settings. On Linux, the Java runtime
inside it keeps a small font cache in `~/.java/fonts`, as every Java program on Linux does.
Uninstalling removes everything the installer put in place.
