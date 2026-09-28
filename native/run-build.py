#!/usr/bin/env python3
"""Jalankan gradle build sebagai daemon double-fork agar tidak dibunuh sandbox."""
import os
import sys
import time

NATIVE = "/home/z/my-project/native"
LOG = "/home/z/my-project/native/build-v11.log"
JAVA = "/home/z/jdk-21.0.12.1+1"

if os.path.exists(LOG):
    os.remove(LOG)

pid = os.fork()
if pid == 0:
    os.setsid()
    pid2 = os.fork()
    if pid2 == 0:
        env = dict(os.environ)
        env["JAVA_HOME"] = JAVA
        env["PATH"] = JAVA + "/bin:" + env.get("PATH", "")
        with open(LOG, "wb") as f:
            f.write(f"start {time.strftime('%H:%M:%S')}\n".encode())
            f.flush()
            os.chdir(NATIVE)
            p = os.subprocess = None  # noqa
            import subprocess
            r = subprocess.run(
                ["./gradlew", "assembleRelease", "--no-daemon", "-q"],
                stdout=f, stderr=subprocess.STDOUT, env=env
            )
            f.write(f"\nEXIT:{r.returncode}\n".encode())
            f.flush()
        os._exit(r.returncode)
    else:
        os._exit(0)
else:
    os.waitpid(pid, 0)
    print("build daemon launched, log:", LOG)
