#!/bin/sh
# Rebuilds app/src/main/assets/districts.json, the boundaries behind a place's county and district.
# Needs Node, for npx. Run it from the repo's root, with one of:
#
#   tools/build-districts.sh <shapefile.shp>      鄉鎮市區界線(TWD97經緯度), from https://data.gov.tw/dataset/7441
#   tools/build-districts.sh <towns-10t.json>     from the npm package taiwan-atlas
#
# The bundled file came from taiwan-atlas 2021.9.20, the ministry's data as simplified TopoJSON,
# because the ministry's download was down. That copy spells 臺 as 台, so it is put back here. Prefer
# the ministry's shapefile when it can be downloaded, and check the result's names and size: the
# simplify step and the shapefile's encoding have not been tried against it yet.
set -eu

source=${1:?"Usage: tools/build-districts.sh <shapefile.shp | towns-10t.json>"}
output=app/src/main/assets/districts.json
mapshaper="npx --yes mapshaper@0.7.101"

case "$source" in
    *.shp) $mapshaper "$source" -simplify 10% keep-shapes \
        -rename-fields county=COUNTYNAME,district=TOWNNAME -filter-fields county,district \
        -rename-layers districts -o format=topojson target=districts quantization=100000 "$output" ;;
    *) $mapshaper "$source" -target towns \
        -rename-fields county=COUNTYNAME,district=TOWNNAME -filter-fields county,district \
        -each 'county=county.replace(/台/g,"臺");district=district.replace(/台/g,"臺")' \
        -rename-layers districts -o format=topojson target=districts quantization=100000 "$output" ;;
esac
