package com.admc.gradle

import org.gradle.api.Project
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import static org.junit.Assert.*

class ContentAsStringFilterTest {
    private Project project
    private File gradleFile
    private File destDir

    {
        project = ProjectBuilder.builder().build()
        destDir = new File('build/tmp/test-work/' +  getClass().simpleName)
        if (destDir.exists() && System.properties['RETAIN_WORK'] == null)
            throw new IllegalStateException(
                    "Ephemeral dir '$destDir.absolutePath' pre-exists")
        if (!destDir.exists() && !destDir.mkdirs())
            throw new IOException(
                    "Failed to make dir '$destDir.absolutePath'")
System.err.println("Iz " + destDir.absolutePath)
        gradleFile = File.createTempFile(getClass().simpleName, '.gradle')
        gradleFile.deleteOnExit()
    }

    private static File mkTextFile() {
        File newFile = File.createTempFile(getClass().simpleName, '.txt')
        newFile.deleteOnExit()
        return newFile
    }

    private static File textFileFromClasspath(String baseName) {
        URL url = Thread.currentThread().contextClassLoader.getResource(
                baseName + '.txt')
        assert url != null:
            ("""Text file not found as resource in classpath:  $baseName"""
            + '''.txt
''')
        return new File(url.toURI())
    }

    @org.junit.After
    void wipeWorkDir() {
        if (System.properties['RETAIN_WORK'] == null) destDir.deleteDir()
    }

    @org.junit.Test
    void otherTst() {}

    @org.junit.Test
    void capitalize() {
        File locDdir = destDir
        File inFile1 = ContentAsStringFilterTest.textFileFromClasspath('triv1')
        File inFile2 = ContentAsStringFilterTest.textFileFromClasspath('triv2')
        project.copy {
            from([inFile1, inFile2])
            into locDdir.absolutePath
            filter(ContentAsStringFilter, closure: { it.toUpperCase() })
        }
        assertEquals("ONE\nTWO\n", new File(destDir, inFile1.name).text)
        assertEquals("THREE\nFOUR\n", new File(destDir, inFile2.name).text)
    }
}
