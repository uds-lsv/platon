Platon
======

Platon is a domain-specific language for authoring 
dialog systems based on Groovy, a dynamic programming language for 
the Java Virtual Machine (JVM).
It is a fully-featured tool for dialog management that is also 
particularly suitable for, but not limited to, rapid prototyping,
making it possible to create a basic multilingual dialog system with
minimal overhead and then gradually extend it to a complete system.
It supports multilinguality, multiple users in a single session, and
has built-in support for interacting with objects in the dialog
environment. It is possible to integrate external components for
natural language understanding and generation, while Platon can
itself be integrated even in non-JVM projects or run in a stand-alone
debugging tool for testing.


Maven
-----

Add this dependency to the dependencies section in your pom.xml:

    <dependency>
        <groupId>de.uds.lsv.dialog</groupId>
        <artifactId>platon</artifactId>
        <version>0.1-SNAPSHOT</version>
    </dependency>

And to enable the repository, add the following lines to the repositories section:

    <repository>
        <id>platon</id>
        <url>https://raw.github.com/uds-lsv/platon/mvn-repo/</url>
        <snapshots>
            <enabled>true</enabled>
            <updatePolicy>always</updatePolicy>
        </snapshots>
    </repository>
