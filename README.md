# osm-address-extractor

## Description

Simple Java extractor of "city -> street -> address" hierarchy from OpenStreetMap. Created as single purpose tool so I guess it won't work for other countries that Czech Republic (because mapping rules may differ). I just publish it for insipiration.

May be there exists tools doing same task faster and better, but I didn't find any suitable fast enough. Also it seems there is no public database of addresses extracted from OSM.

## OpenStreetMap

OSM database (*.osm, *.pbf) is mixture of entities bounded by:
* Classical "id" relations. So for example ways describing boundary of city are linked into one relation. This can be easily extracted.
* Entities bounded purely by geolocation. So for example streets of city are those placed within city boundary. This is harder to extract.

Order of section within OSM (http://wiki.openstreetmap.org/wiki/OSM_XML#OSM_XML_file_format) files is following:
1. nodes
2. ways
3. relations

Typically file size of OSM is in order of gigabytes - Czech Republic has 8.9GB osm (377MB pbf) and contains 40M objects. My initial idea was that I will store all objects in memory and perform extraction directly. I soon find out this is not possible on 16GB of RAM. So I decided to used stream approach.

Because of previous facts (order of section, hierarchy of objects, ...) it is necessary to do multiple passes of source file. For example:

0. obtain all cities and streets
1. obtain boundary ways of city, obtain points of streets
2. obtain points of boundary ways

In each pass load requirements for next pass are collected. For example for street there the load requirements are all nodes defining it. By this approach you are able to load only necessary objects and keep memory requirements feasible.

## Used tools

Probably it would make a sense to use geo-db and other gis tools, I just wanted to keep is simple.

* http://wiki.openstreetmap.org/wiki/Osmosis
* http://www.vividsolutions.com/jts/JTSHome.htm

## Useful links

* map features description: http://wiki.openstreetmap.org/wiki/Cs:Map_Features
* boundary administrative: http://wiki.openstreetmap.org/wiki/Tag:boundary%3Dadministrative
* Czech data: http://osm.kyblsoft.cz/archiv/
* 
