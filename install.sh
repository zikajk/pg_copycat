#!/bin/bash
if ! type bb &> /dev/null
then
    curl -sLO https://raw.githubusercontent.com/babashka/babashka/master/install
    chmod +x install
    ./install
    rm install
    echo 'Babashka has been installed!'
else
    echo 'Babashka has been installed already.. SKIPPING!'
fi

echo 'Copying to ~/bin/pg_copycat'
cp build/pg_copycat ~/bin/pg_copycat
echo 'Goodbye!'

