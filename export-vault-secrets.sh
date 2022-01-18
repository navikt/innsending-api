#!/usr/bin/env sh

if test -f /var/run/secrets/nais.io/serviceuser/username;
then
    echo "Setting APPLICATION_USERNAME"
    export APPLICATION_USERNAME=$(cat /var/run/secrets/nais.io/srvtilbakemeldingsmottak/username)
fi

if test -f /var/run/secrets/nais.io/serviceuser/password;
then
    echo "Setting APPLICATION_PASSWORD"
    export APPLICATION_PASSWORD=$(cat /var/run/secrets/nais.io/srvtilbakemeldingsmottak/password)
fi

if test -f secret/team-soknad/innsending/basicauth/username;
then
    echo "Setting SHARED_USERNAME"
    export SHARED_USERNAME=$(cat secret/team-soknad/innsending/basicauth/username)
fi

if test -f secret/team-soknad/innsending/basicauth/password;
then
    echo "Setting SHARED_PASSWORD"
    export SHARED_PASSWORD=$(cat secret/team-soknad/innsending/basicauth/password)
fi
