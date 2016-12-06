FROM java:openjdk-8-alpine

MAINTAINER jonathan.creasy@gmail.com

ENV       VERSION 2.3.0-RC1
ENV       WORKDIR /usr/share/opentsdb
ENV       LOGDIR  /var/log/opentsdb
ENV       ETCDIR /etc/opentsdb

RUN       mkdir -p $WORKDIR/build/libs
RUN       mkdir -p $LOGDIR
RUN       mkdir -p $ETCDIR

ENV       CONFIG $ETCDIR/opentsdb.conf

ENV       JAR DiscoveryPlugins-all-1.0-SNAPSHOT.jar

# It is expected these might need to be passed in with the -e flag
ENV       JAVA_OPTS="-Xms512m -Xmx2048m"
ENV       ZKQUORUM zookeeper:2181
ENV       TSDB_OPTS "--read-only --disable-ui"
ENV       TSDB_PORT  4244

WORKDIR   $WORKDIR

ADD       build/libs/$JAR $WORKDIR/build/libs/$JAR
ADD       src/main/resources/opentsdb.conf $CONFIG

VOLUME    ["/etc/opentsdb"]

CMD java -jar build/libs/${JAR} --config=${CONFIG}
