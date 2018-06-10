# clj

LiveJournal archive tool written in Clojure.
Saves all journal entries and comments in XML format.

[![Build Status](https://travis-ci.org/alex-vv/clj.svg?branch=master)](https://travis-ci.org/alex-vv/clj)

## Usage

* Make sure you have Java installed (version 8 or later)

* Download the latest CLJ jar file from Releases:

https://github.com/alex-vv/clj/releases/download/2.0.0-M1/clj-2.0.0-M1-standalone.jar

* Run with Java

```
$ java -jar clj-2.0.0-M1-standalone.jar username [password] [journal]
```

**Parameters:**

`username` - LiveJournal user name which is also a journal name which will be downloaded unless a separate journal is specified in `journal` parameter.

`password` - LiveJournal user password. Optional, if not set then only the public entries will be downloaded.

`journal` - Allows to specify a separate journal to download. Optional, if not set will fetch user's own journal.


## Alternative way

 * Install Leiningen, for example with Homebrew on mac OS
 ```
 $ brew install leiningen
 ```
 * Run with lein
 ```
 $ lein run username password [journal]
 ```

## License

This project is licensed under the terms of the MIT license.
