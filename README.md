# osm-address-extractor

## Description

Simple Java extractor of "district->city -> street -> address" hierarchy from OpenStreetMap. Created as single purpose tool (for http://www.nalezen.cz) so I guess it won't work for other countries that Czech Republic (because mapping rules may differ). There are also country-specific data sources. _I just publish it for insipiration._

May be there exists tools doing same task faster and better, but I didn't find any suitable fast enough. Also it seems there is no public database of addresses extracted from OSM.

![Sample visualization](/sample_vis.png "Sample visualization")

## Usage

Build is based on Gradle:
```
gradle eclipse
gradle build
```

Sample:

```
CzAddressExtractor czAddressExtractor = new CzAddressExtractor();

//import of MVCR addresses (defines possible districts-cities-streets-addresses)
czAddressExtractor.importMvcrData("/home/jarek/geo_db/src/adresy.xml");
		
//import of Czech Post data (enrich cities by postcodes)
czAddressExtractor.importCzechPostData("/home/jarek/geo_db/src/psc.csv");
		
//import OSM data, enrich addresses by geolocation (if possible)
czAddressExtractor.importOsmData("/home/jarek/geo_db/src/czech_republic-2014-03-05.osm.pbf");
		
//save result
czAddressExtractor.save("/home/jarek/geo_db/cz_addresses_new.json");

```
## Log4j configuration

```
log4j.logger.cz.nalezen.osm.extractor.mvcr.MvcrSaxHandler=info,ChytristorMain
log4j.logger.cz.nalezen.osm.extractor.cp.CzechPostDataLoader=info,ChytristorMain
log4j.logger.cz.nalezen.chytristor.logic.GeoinfoLoader=info,ChytristorMain
log4j.logger.cz.nalezen.osm.extractor.osm.GeoExtractor=info,ChytristorMain
log4j.logger.cz.nalezen.osm.extractor.osm.AddressTreeLinker=info,ChytristorMain
```

## Data quality

As this tool works fully automatically, quality of output is poor. There is plenty of cases when data from MVCR does not match data from OSM. For example street "Lidická" vs. "Lidická třída".

But for purposes of http://www.nalezen.cz is quality good enoug.


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

In each pass load requirements for next pass are collected. For example for street there the load requirements are all nodes defining it. When there is no new request during pass, iteration ends. Then everything necessary is loaded and addresses may be extracted. By this approach you are able to load only necessary objects and keep memory requirements feasible.

## Used tools

Probably it would make a sense to use geo-db and other gis tools, I just wanted to keep is simple.

* http://wiki.openstreetmap.org/wiki/Osmosis
* http://www.vividsolutions.com/jts/JTSHome.htm

## Useful links

Specifications:
* Pap features description: http://wiki.openstreetmap.org/wiki/Cs:Map_Features
* Boundary administrative: http://wiki.openstreetmap.org/wiki/Tag:boundary%3Dadministrative
* Address structure: http://wiki.openstreetmap.org/wiki/Cs:WikiProject_Czech_Republic/Address_system

Data for import:
* Czech data: http://osm.kyblsoft.cz/archiv/
* Czech postcodes: http://www.ceskaposta.cz/ke-stazeni/zakaznicke-vystupy ("Seznam PSČ částí obcí a obcí bez částí") then convert into csv using Excel or OpenOffice Calc.
* Addresess db provided by MVCR: http://aplikace.mvcr.cz/adresy/ (data ke stažení)
