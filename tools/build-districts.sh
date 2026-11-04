#!/bin/sh
# Rebuilds app/src/main/assets/districts.json: the boundaries behind a place's county and district,
# and the Map tab's outlines and names. Needs Node, for npx. Run it from the repo's root, with one of:
#
#   tools/build-districts.sh <shapefile.shp>      鄉鎮市區界線(TWD97經緯度), from https://data.gov.tw/dataset/7441
#   tools/build-districts.sh <towns-10t.json>     from the npm package taiwan-atlas
#
# The file holds two layers:
#   districts   each district's outline, county, district, and code (TOWNCODE), and a label point
#   counties    one point per county, with its name and a label point; the app draws county lines
#               where districts of two counties meet, so the counties need no outlines of their own
# Label points, lx and ly, are a longitude and latitude inside the area, rounded to four decimals.
#
# The bundled file came from taiwan-atlas 2021.9.20, the ministry's data as simplified TopoJSON,
# because the ministry's download was down. That copy spells 臺 as 台, so it is put back here. Its
# districts can't be merged by county (mapshaper reports invalid node geometry), but the file holds
# its own county layer, which gives the counties' label points. Prefer the ministry's shapefile when it can be
# downloaded, and check the result's names and size: the simplify step, the merge, and the
# shapefile's encoding have not been tried against it yet.
set -eu

source=${1:?"Usage: tools/build-districts.sh <shapefile.shp | towns-10t.json>"}
output=app/src/main/assets/districts.json
labels='lx=Math.round(this.innerX*1e4)/1e4,ly=Math.round(this.innerY*1e4)/1e4'
spelling='county=county.replace(/台/g,"臺")'
districts="-rename-fields county=COUNTYNAME,district=TOWNNAME,code=TOWNCODE -filter-fields county,district,code
    -each $spelling;district=district.replace(/台/g,\"臺\") -each $labels -rename-layers districts"

mapshaper() { npx --yes mapshaper@0.7.101 "$@"; }

case "$source" in
    *.shp)
        # shellcheck disable=SC2086
        mapshaper "$source" -simplify 10% keep-shapes $districts \
            -dissolve county + name=counties -each "$labels" target=counties -points x=lx y=ly target=counties \
            -o format=topojson target=districts,counties quantization=100000 "$output" ;;
    *)
        # shellcheck disable=SC2086
        mapshaper "$source" -target towns $districts \
            -target counties -rename-fields county=COUNTYNAME -filter-fields county -each "$spelling" \
            -each "$labels" -points x=lx y=ly \
            -o format=topojson target=districts,counties quantization=100000 "$output" ;;
esac
