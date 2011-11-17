package com.admc.gradle

import org.gradle.api.Project
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import static org.junit.Assert.*

class JavaPropFilePluginTest {
    private int counter = 0
    private static File mkTestFile() {
        File newFile = File.createTempFile(
                getClass().simpleName + '', ".properties")
        newFile.deleteOnExit()
        return newFile
    }

    @org.junit.Test
    void trivialPropertySet() {
        Project project = ProjectBuilder.builder().build()
        project.apply plugin: com.admc.gradle.JavaPropFilePlugin
        if (project.hasProperty('alpha'))
            throw new IllegalStateException(
                    '''Project has property 'alpha' before our test began''')

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=one', "ISO-8859-1")
        project.propFileLoader.load(f)

        assertTrue(project.hasProperty('alpha'))
        assertEquals(project.property('alpha'), 'one')
    }

    @org.junit.Test
    void sysProperty() {
        Project project = ProjectBuilder.builder().build()
        project.apply plugin: com.admc.gradle.JavaPropFilePlugin
        if (project.hasProperty('me'))
            throw new IllegalStateException(
                    '''Project has property 'me' before our test began''')

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('me=I am ${user.name}', "ISO-8859-1")
        project.propFileLoader.load(f)

        assertTrue(project.hasProperty('me'))
        assertEquals(project.property('me'),
                'I am ' + System.properties['user.name'])
    }

    @org.junit.Test
    void nest() {
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
        assertEquals(project.property('beta'), 'two')
        assertEquals(project.property('alpha'), 'onetwo')
    }

    @org.junit.Test(expected=GradleException.class)
    void typeCollision() {
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
    void nullCollision() {
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

    @org.junit.Test(expected=GradleException.class)
    void unsetLoneThrow() {
        Project project = ProjectBuilder.builder().build()
        project.apply plugin: com.admc.gradle.JavaPropFilePlugin
        if (project.hasProperty('notset'))
            throw new IllegalStateException(
                    '''Project has property 'notset' before our test began''')
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=${notset}', "ISO-8859-1")
        project.propFileLoader.load(f)
    }

    @org.junit.Test(expected=GradleException.class)
    void unsetSandwichedThrow() {
        Project project = ProjectBuilder.builder().build()
        project.apply plugin: com.admc.gradle.JavaPropFilePlugin
        if (project.hasProperty('notset'))
            throw new IllegalStateException(
                    '''Project has property 'notset' before our test began''')
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=pre${notset}post', "ISO-8859-1")
        project.propFileLoader.load(f)
    }

    @org.junit.Test
    void unsetLoneNoSet() {
        Project project = ProjectBuilder.builder().build()
        project.apply plugin: com.admc.gradle.JavaPropFilePlugin
        if (project.hasProperty('notset'))
            throw new IllegalStateException(
                    '''Project has property 'notset' before our test began''')
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=${notset}', "ISO-8859-1")
        project.propFileLoader.unsatisfiedRefBehavior =
                JavaPropFile.Behavior.NO_SET
        project.propFileLoader.load(f)
        assertFalse(project.hasProperty('alpha'))
        project.setProperty('alpha', 'eins')
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('alpha'))
        assertEquals(project.property('alpha'), 'eins')
    }

    @org.junit.Test
    void unsetSandwichedNoSet() {
        Project project = ProjectBuilder.builder().build()
        project.apply plugin: com.admc.gradle.JavaPropFilePlugin
        if (project.hasProperty('notset'))
            throw new IllegalStateException(
                    '''Project has property 'notset' before our test began''')
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=pre${notset}post', "ISO-8859-1")
        project.propFileLoader.unsatisfiedRefBehavior =
                JavaPropFile.Behavior.NO_SET
        project.propFileLoader.load(f)
        assertFalse(project.hasProperty('alpha'))
        project.setProperty('alpha', 'eins')
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('alpha'))
        assertEquals(project.property('alpha'), 'eins')
    }

    /*  See comment about Behavior.UNSET in JavaPropFile.java.
    @org.junit.Test
    void unsetLoneUnSet() {
        Project project = ProjectBuilder.builder().build()
        project.apply plugin: com.admc.gradle.JavaPropFilePlugin
        if (project.hasProperty('notset'))
            throw new IllegalStateException(
                    '''Project has property 'notset' before our test began''')
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=${notset}', "ISO-8859-1")
        project.propFileLoader.unsatisfiedRefBehavior =
                JavaPropFile.Behavior.UNSET
        project.propFileLoader.load(f)
        assertFalse(project.hasProperty('alpha'))
        project.setProperty('alpha', 'eins')
        project.propFileLoader.load(f)
        assertFalse(project.hasProperty('alpha'))
    }

    @org.junit.Test
    void unsetSandwichedUnSet() {
        Project project = ProjectBuilder.builder().build()
        project.apply plugin: com.admc.gradle.JavaPropFilePlugin
        if (project.hasProperty('notset'))
            throw new IllegalStateException(
                    '''Project has property 'notset' before our test began''')
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=pre${notset}post', "ISO-8859-1")
        project.propFileLoader.unsatisfiedRefBehavior =
                JavaPropFile.Behavior.UNSET
        project.propFileLoader.load(f)
        assertFalse(project.hasProperty('alpha'))
        project.setProperty('alpha', 'eins')
        project.propFileLoader.load(f)
        assertFalse(project.hasProperty('alpha'))
    }
    */

    @org.junit.Test
    void unsetLoneLiteral() {
        Project project = ProjectBuilder.builder().build()
        project.apply plugin: com.admc.gradle.JavaPropFilePlugin
        if (project.hasProperty('notset'))
            throw new IllegalStateException(
                    '''Project has property 'notset' before our test began''')
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=${notset}', "ISO-8859-1")
        project.propFileLoader.unsatisfiedRefBehavior =
                JavaPropFile.Behavior.LITERAL
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('alpha'))
        assertEquals(project.property('alpha'), '${notset}')
    }

    @org.junit.Test
    void unsetSandwichedLiteral() {
        Project project = ProjectBuilder.builder().build()
        project.apply plugin: com.admc.gradle.JavaPropFilePlugin
        if (project.hasProperty('notset'))
            throw new IllegalStateException(
                    '''Project has property 'notset' before our test began''')
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=pre${notset}post', "ISO-8859-1")
        project.propFileLoader.unsatisfiedRefBehavior =
                JavaPropFile.Behavior.LITERAL
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('alpha'))
        assertEquals(project.property('alpha'), 'pre${notset}post')
    }

    @org.junit.Test
    void unsetLoneEmpty() {
        Project project = ProjectBuilder.builder().build()
        project.apply plugin: com.admc.gradle.JavaPropFilePlugin
        if (project.hasProperty('notset'))
            throw new IllegalStateException(
                    '''Project has property 'notset' before our test began''')
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=${notset}', "ISO-8859-1")
        project.propFileLoader.unsatisfiedRefBehavior =
                JavaPropFile.Behavior.EMPTY
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('alpha'))
        assertEquals(project.property('alpha'), '')
    }

    @org.junit.Test
    void unsetSandwichedEmpty() {
        Project project = ProjectBuilder.builder().build()
        project.apply plugin: com.admc.gradle.JavaPropFilePlugin
        if (project.hasProperty('notset'))
            throw new IllegalStateException(
                    '''Project has property 'notset' before our test began''')
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=pre${notset}post', "ISO-8859-1")
        project.propFileLoader.unsatisfiedRefBehavior =
                JavaPropFile.Behavior.EMPTY
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('alpha'))
        assertEquals(project.property('alpha'), 'prepost')
    }

    @org.junit.Test
    void traditionalSanityCheck() {
        // Can't test much specifically, but we know that the method can at
        // least be called.
        Project project = ProjectBuilder.builder().build()
        project.apply plugin: com.admc.gradle.JavaPropFilePlugin
        project.propFileLoader.traditionalPropertiesInit()
    }
}
