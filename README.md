# TMEngine

![alt text](https://maxprograms.com/images/tmengine_s.png "TMEngine")

An open source [Translation Memory](https://en.wikipedia.org/wiki/Translation_memory) Engine written in Java.

TMEngine can be used either as an embedded library that manages translation memories in a Java application or as a standalone TM server via its REST API.

## Requirements

- JDK 11 or newer is required for compiling and building.
- Apache Ant 1.10.6 or newer

## Building

- Checkout this repository.
- Point your JAVA_HOME variable to JDK 11
- Run `ant compile`.

## Standalone Server

Use `tmserver.bat` or `tmserver.sh` to launch the TMEngine server.

## Java Library 

Interface `ITmEngine` provides the following methods needed by translation tools:

- `storeTMX()` - Store the content of a TMX file into a translation memory.
- `searchTranslation()` - Search for matches of a text string.
- `concordanceSearch()` - Search for appearances of a given string inside memory segments.
- `exportMemory()` - Export the contents of a translation memory to a TMX file.

Two classes implement interface `ITmEngine`:

- `MapDbEngine` : a translation memory engine built using MapDB
- `SQLEngine` : a translation memory designed to be used with [MariaDB](https://mariadb.org/) or [MySQL](https://www.mysql.com/)
