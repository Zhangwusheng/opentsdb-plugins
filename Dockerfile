FROM java:openjdk-8-alpine

MAINTAINER jonathan.creasy@gmail.com

ARG       VERSION
ENV       JARVERSION=$VERSION
ENV       WORKDIR /usr/share/opentsdb
ENV       LOGDIR  /var/log/opentsdb
ENV       ETCDIR /etc/opentsdb

RUN       mkdir -p $WORKDIR/build/libs
RUN       mkdir -p $LOGDIR
RUN       mkdir -p $ETCDIR

# It is expected these might need to be passed in with the -e flag
ENV       JAVA_OPTS="-Xms512m -Xmx2048m"

WORKDIR   $WORKDIR

ADD       build/install/DiscoveryPlugins/lib/* $WORKDIR/build/libs/
ADD       build/install/DiscoveryPlugins/bin/* $WORKDIR/bin/
ADD       src/main/resources/opentsdb.conf $ETCDIR/opentsdb.conf
ADD       src/main/resources/logback.xml $WORKDIR/build/libs/logback.xml

VOLUME    ["/etc/opentsdb"]

CMD java ${JAVA_OPTS} -cp "build/libs:build/libs/logback.xml:build/libs/*"  io.tsdb.opentsdb.ExecutePlugin --config=${ETCDIR}/opentsdb.conf
