bb uberscript pg_copycat.clj -m pg-copycat.core
rm -f pg_copycat
echo '#!/usr/bin/env bb' >> pg_copycat
cat pg_copycat.clj >> pg_copycat
chmod +rx pg_copycat
mv pg_copycat build/
rm pg_copycat.clj


