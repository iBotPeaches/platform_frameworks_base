#!/system/bin/sh

if [ "$1" != "instrument" ] ; then
    cmd activity "$@"
else
    # set to top-app process group for instrument
    settaskprofile $$ SCHED_SP_TOP_APP >/dev/null 2>&1 || true
    base=/system
    export CLASSPATH=$base/framework/am.jar
    exec app_process $base/bin com.android.commands.am.Am "$@"
fi
