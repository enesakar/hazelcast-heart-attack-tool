#!/bin/sh

if [ -z "${HEART_ATTACK_HOME}" ] ; then
    export HEART_ATTACK_HOME=$(cd $(dirname $(readlink -f $0 2> /dev/null || readlink $0 2> /dev/null || echo $0))/.. && pwd)
fi

echo  HEART_ATTACK_HOME = ${HEART_ATTACK_HOME}

export JAVA_OPTS="-Xms256m -Xmx1g -XX:MaxPermSize=256m"
java -cp "${HEART_ATTACK_HOME}/lib/*" ${JAVA_OPTS} com.hazelcast.heartattack.Coach "$@"