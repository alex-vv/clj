# clj

LiveJournal archive tool written in Clojure.
Saves all journal entries and comments in XML format.

[![Build Status](https://travis-ci.org/alex-vv/clj.svg?branch=master)](https://travis-ci.org/alex-vv/clj)

## Usage

* Make sure you have Java installed (version 8 or later)

* Download the latest CLJ jar file from Releases:

https://github.com/alex-vv/clj/releases/download/2.0.1/clj-2.0.1-standalone.jar

* Run with Java

```
$ java -jar clj-2.0.1-standalone.jar username [journal] [-p]
```

**Parameters:**

`username` - LiveJournal user name which is also a journal name which will be downloaded unless a separate journal is specified in `journal` parameter.

`journal` - Allows to specify a separate journal to download. Optional, if not set will fetch user's own journal.

`-p` - Ask for LiveJournal user's password. Optional, if not set then only the public entries will be downloaded.  It is also possible to provide a password with `CLJ_PASSWORD` environmental variable.


## Alternative way

 * Install Leiningen, for example with Homebrew on mac OS
 ```
 $ brew install leiningen
 ```
 * Run with lein
 ```
 $ lein run username [password] [journal]
 ```

## License

This project is licensed under the terms of the MIT license.
