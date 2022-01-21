#!/usr/bin/env sh

if test -f /var/run/secrets/nais.io/serviceuser/username;
then
    echo "Setting APPLICATION_USERNAME"
    export APPLICATION_USERNAME=$(cat /var/run/secrets/nais.io/serviceuser/username)
fi

if test -f /var/run/secrets/nais.io/serviceuser/password;
then
    echo "Setting APPLICATION_PASSWORD"
    export APPLICATION_PASSWORD=$(cat /var/run/secrets/nais.io/serviceuser/password)
fi

if test -f /secrets/innsending-data/username;
then
    echo "Setting SHARED_USERNAME"
    export SHARED_USERNAME=$(cat /secrets/innsending-data/username)
fi

if test -f /secrets/innsending-data/password;
then
    echo "Setting SHARED_PASSWORD"
    export SHARED_PASSWORD=$(cat /secrets/innsending-data/password)
fi
