# TMEngine

<img src="https://www.maxprograms.com/images/tmengine_s.png" alt="TMEngine logo"/>

An open source [Translation Memory](https://en.wikipedia.org/wiki/Translation_memory) Engine written in Java.

TMEngine is based on the translation memory library used by [Swordfish III](https://www.maxprograms.com/products/swordfish.html), [Fluenta](https://www.maxprograms.com/products/fluenta.html) and [RemoteTM](https://www.maxprograms.com/products/remotetm.html).

TMEngine can be used either as an embedded library that manages translation memories in a Java application or as a standalone TM server via its REST API.

## Requirements

- JDK 11 or newer is required for compiling and building.
- Apache Ant 1.10.6 or newer

## Building

- Checkout this repository.
- Point your JAVA_HOME variable to JDK 11
- Run `ant compile`.

## Downloads

Ready to use distributions are available at [https://www.maxprograms.com/products/tmengine.html](https://www.maxprograms.com/products/tmengine.html).

## Related Links

- [TMEngine Manual (PDF)](https://www.maxprograms.com/support/tmengine.pdf)
- [TMEgine Manual (Web Help)](https://www.maxprograms.com/support/tmengine/TMEngine.html)

## Standalone Server

Running `.\tmserver.bat` or `./tmserver.sh` without parameters displays help for starting TMEngine as a standalone server.
```
Usage:
                
      tmserver.sh [-help] [-version] [-port portNumber]
                
      Where:
                
      -help:      (optional) Display this help information and exit
      -version:   (optional) Display version & build information and exit
      -port:      (optional) Port for running HTTP server. Default is 8000
```

Visit http://localhost:8000/TMServer/stop to stop the server. Adjust the port number if required.

## Java Library

TMEngine can be embedded in Java applications that need to deal with Translation Memory data.

Add all .jar files from `/lib` folder to the classpath of your application and use instances of `ITmEngine` interface.

Two classes implement interface `ITmEngine`:

- `MapDbEngine` : a translation memory engine built using [MapDB](http://mapdb.org)
- `SQLEngine` : a translation memory designed to be used with [MariaDB](https://mariadb.org/) or [MySQL](https://www.mysql.com/)

See more details on the available Java methods in the [documentation](https://www.maxprograms.com/support/tmengine/TMEngine.html).
