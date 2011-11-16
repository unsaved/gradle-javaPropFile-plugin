package com.admc.gradle;

import org.gradle.api.Project
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import static org.junit.Assert.*

class JavaPropFilePluginTest {
    private int counter = 0;
    private static File mkTestFile() {
        File newFile = File.createTempFile(
                getClass().simpleName + '', ".properties")
        newFile.deleteOnExit()
        return newFile
    }

    @org.junit.Test
    public void trivialPropertySet() {
        Project project = ProjectBuilder.builder().build()
        project.apply plugin: com.admc.gradle.JavaPropFilePlugin
        if (project.hasProperty('alpha'))
            throw new IllegalStateException(
                    '''Project has property 'alpha' before our test began''')

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=one', "ISO-8859-1")
        project.propFileLoader.load(f)

        assertTrue(project.hasProperty('alpha'))
        assertTrue(project.property('alpha') == 'one')
    }

    @org.junit.Test
    public void sysProperty() {
        Project project = ProjectBuilder.builder().build()
        project.apply plugin: com.admc.gradle.JavaPropFilePlugin
        if (project.hasProperty('me'))
            throw new IllegalStateException(
                    '''Project has property 'me' before our test began''')

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('me=I am ${user.name}', "ISO-8859-1")
        project.propFileLoader.load(f)

        assertTrue(project.hasProperty('me'))
        assertTrue(project.property('me')
                == ('I am ' + System.properties['user.name']))
    }

    @org.junit.Test
    public void nest() {
        Project project = ProjectBuilder.builder().build()
        project.apply plugin: com.admc.gradle.JavaPropFilePlugin
        if (project.hasProperty('alpha'))
            throw new IllegalStateException(
                    '''Project has property 'alpha' before our test began''')
        if (project.hasProperty('beta'))
            throw new IllegalStateException(
                    '''Project has property 'beta' before our test began''')

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=one${beta}\nbeta=two', "ISO-8859-1")
        project.propFileLoader.load(f)

        assertTrue(project.hasProperty('alpha'))
        assertTrue(project.hasProperty('beta'))
        assertTrue(project.property('beta') == 'two')
        assertTrue(project.property('alpha') == 'onetwo')
    }

    @org.junit.Test(expected=GradleException.class)
    public void typeCollision() {
        Project project = ProjectBuilder.builder().build()
        project.apply plugin: com.admc.gradle.JavaPropFilePlugin
        if (project.hasProperty('aFile'))
            throw new IllegalStateException(
                    '''Project has property 'aFile' before our test began''')
        project.aFile = new File('x.txt')

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('aFile=one', "ISO-8859-1")
        project.propFileLoader.load(f)
    }

    @org.junit.Test(expected=GradleException.class)
    public void nullCollision() {
        Project project = ProjectBuilder.builder().build()
        project.apply plugin: com.admc.gradle.JavaPropFilePlugin
        if (project.hasProperty('aNull'))
            throw new IllegalStateException(
                    '''Project has property 'aNull' before our test began''')
        project.aNull = (String) null

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('aNull=one', "ISO-8859-1")
        project.propFileLoader.load(f)
    }
}
