#!/bin/sh

usage() { echo "Usage: $0 -h <hostname>" 1>&2; exit 1; }

while getopts ":h:" o; do
    case "${o}" in
        h)
            host=${OPTARG}
            ;;
        *)
            usage
            ;;
    esac
done
shift $((OPTIND-1))

if [ -z "${host}" ] ; then
    usage
fi

echo "Copying..."

user=root

rsync -v target/camel-form-emailer-1.0.0-SNAPSHOT-runner ${user}@${host}:/opt/cformeml/cformeml
ssh -t ${user}@${host} "chown cformeml:cformeml /opt/cformeml/cformeml"

echo "Copied."

