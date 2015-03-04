DOMAIN_PATH=$1/*

for f in $DOMAIN_PATH; do
    if [ -d "$f" ];
    then
	echo "Creating $f"
	./src/scripts/cobot/language_geography/create_domain.sh $f
    fi
done
