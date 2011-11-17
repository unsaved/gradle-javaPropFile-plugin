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

    private static Project prepProject(String checkProp) {
        return prepProject([checkProp])
    }

    private static Project prepProject(List<String> checkProps) {
        Project proj = ProjectBuilder.builder().build()
        proj.apply plugin: com.admc.gradle.JavaPropFilePlugin
        if (checkProps == null) return proj

        for (p in checkProps) {
            assert !p.hasProperty(p):
                "Gradle project has property '$p' set before test begins"
            assert System.properties[p] == null:
                "Java system property '$p' is set before test begins"
        }
        return proj
    }

    @org.junit.Test
    void trivialPropertySet() {
        Project project = JavaPropFilePluginTest.prepProject('alpha')

        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=one', 'ISO-8859-1')
        project.propFileLoader.load(f)

        assertTrue(project.hasProperty('alpha'))
        assertEquals('one', project.property('alpha'))
    }

    @org.junit.Test
    void sysProperty() {
        Project project = JavaPropFilePluginTest.prepProject('me')

        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('me=I am ${user.name}', 'ISO-8859-1')
        project.propFileLoader.load(f)

        assertTrue(project.hasProperty('me'))
        assertEquals( 'I am ' + System.properties['user.name'],
                project.property('me'))
    }

    @org.junit.Test
    void nest() {
        Project project = JavaPropFilePluginTest.prepProject(['alpha', 'beta'])

        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=one${beta}\nbeta=two', 'ISO-8859-1')
        project.propFileLoader.load(f)

        assertTrue(project.hasProperty('alpha'))
        assertTrue(project.hasProperty('beta'))
        assertEquals('two', project.property('beta'))
        assertEquals('onetwo', project.property('alpha'))
    }

    @org.junit.Test(expected=GradleException.class)
    void typeCollision() {
        Project project = JavaPropFilePluginTest.prepProject('aFile')

        project.aFile = new File('x.txt')
        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('aFile=one', 'ISO-8859-1')
        project.propFileLoader.load(f)
    }

    @org.junit.Test(expected=GradleException.class)
    void nullCollision() {
        Project project = JavaPropFilePluginTest.prepProject('aNull')
        project.setProperty('aNull', (String) null)

        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('aNull=one', 'ISO-8859-1')
        project.propFileLoader.load(f)
    }

    @org.junit.Test(expected=GradleException.class)
    void unsetLoneThrow() {
        Project project =
                JavaPropFilePluginTest.prepProject(['notset', 'alpha'])

        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=${notset}', 'ISO-8859-1')
        project.propFileLoader.load(f)
    }

    @org.junit.Test(expected=GradleException.class)
    void unsetSandwichedThrow() {
        Project project = JavaPropFilePluginTest.prepProject('alpha')

        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=pre${notset}post', 'ISO-8859-1')
        project.propFileLoader.load(f)
    }

    @org.junit.Test
    void unsetLoneNoSet() {
        Project project = JavaPropFilePluginTest.prepProject('alpha')

        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=${notset}', 'ISO-8859-1')
        project.propFileLoader.unsatisfiedRefBehavior =
                JavaPropFile.Behavior.NO_SET
        project.propFileLoader.load(f)
        assertFalse(project.hasProperty('alpha'))
        project.setProperty('alpha', 'eins')
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('alpha'))
        assertEquals('eins', project.property('alpha'))
    }

    @org.junit.Test
    void unsetSandwichedNoSet() {
        Project project = JavaPropFilePluginTest.prepProject('alpha')

        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=pre${notset}post', 'ISO-8859-1')
        project.propFileLoader.unsatisfiedRefBehavior =
                JavaPropFile.Behavior.NO_SET
        project.propFileLoader.load(f)
        assertFalse(project.hasProperty('alpha'))
        project.setProperty('alpha', 'eins')
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('alpha'))
        assertEquals('eins', project.property('alpha'))
    }

    /*  See comment about Behavior.UNSET in JavaPropFile.java.
    @org.junit.Test
    void unsetLoneUnSet() {
        Project project = JavaPropFilePluginTest.prepProject('alpha')

        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=${notset}', 'ISO-8859-1')
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
        Project project = JavaPropFilePluginTest.prepProject('alpha')

        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=pre${notset}post', 'ISO-8859-1')
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
        Project project = JavaPropFilePluginTest.prepProject('alpha')

        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=${notset}', 'ISO-8859-1')
        project.propFileLoader.unsatisfiedRefBehavior =
                JavaPropFile.Behavior.LITERAL
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('alpha'))
        assertEquals('${notset}', project.property('alpha'))
    }

    @org.junit.Test
    void unsetSandwichedLiteral() {
        Project project = JavaPropFilePluginTest.prepProject('alpha')

        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=pre${notset}post', 'ISO-8859-1')
        project.propFileLoader.unsatisfiedRefBehavior =
                JavaPropFile.Behavior.LITERAL
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('alpha'))
        assertEquals('pre${notset}post', project.property('alpha'))
    }

    @org.junit.Test
    void unsetLoneEmpty() {
        Project project = JavaPropFilePluginTest.prepProject('alpha')

        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=${notset}', 'ISO-8859-1')
        project.propFileLoader.unsatisfiedRefBehavior =
                JavaPropFile.Behavior.EMPTY
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('alpha'))
        assertEquals('', project.property('alpha'))
    }

    @org.junit.Test
    void unsetSandwichedEmpty() {
        Project project = JavaPropFilePluginTest.prepProject('alpha')

        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=pre${notset}post', 'ISO-8859-1')
        project.propFileLoader.unsatisfiedRefBehavior =
                JavaPropFile.Behavior.EMPTY
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('alpha'))
        assertEquals('prepost', project.property('alpha'))
    }

    @org.junit.Test
    void deeplyNested() {
        Project project = JavaPropFilePluginTest.prepProject(
                ['bottom1', 'mid2', 'mid3a', 'mid3b', 'top4'])

        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('''
# White space in settings is to test that it is tolerated and ignored
mid3a=m3a ${bottom1} ${mid2}
top4=t4 ${mid3a} ${mid3b} ${bottom1}
    mid3b   m3b ${mid2}
bottom1  =  Bottom
mid2   m2 ${bottom1}
''', 'ISO-8859-1')
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('top4'))
        assertEquals('t4 m3a Bottom m2 Bottom m3b m2 Bottom Bottom',
                project.property('top4'))
    }

    @org.junit.Test
    void overwrite() {
        Project project = JavaPropFilePluginTest.prepProject(['alpha', 'beta'])

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=replacement\nbeta=${beta} addition')
        project.setProperty('alpha', 'eins')
        project.setProperty('beta', 'zwei')
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('alpha'))
        assertTrue(project.hasProperty('beta'))
        assertEquals('replacement', project.property('alpha'))
        assertEquals('zwei addition', project.property('beta'))
    }

    @org.junit.Test
    void noOverwrite() {
        Project project = JavaPropFilePluginTest.prepProject(['alpha', 'beta'])

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=replacement\nbeta=${beta} addition')
        project.setProperty('alpha', 'eins')
        project.setProperty('beta', 'zwei')
        project.propFileLoader.overwrite = false
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('alpha'))
        assertTrue(project.hasProperty('beta'))
        assertEquals('eins', project.property('alpha'))
        assertEquals('zwei', project.property('beta'))
    }

    @org.junit.Test
    void setSysProps() {
        Project project = prepProject([
            'alpha', 'systemProp.file.separator', 'systemProp.slpha'
        ])
        assert !project.hasProperty('file.separator'):
            '''Project has property 'file.separator' set before we start test'''
        
        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('systemProp.alpha=eins\nsystemProp.file.separator=*')
        project.propFileLoader.systemPropPrefix = 'systemProp.'
        project.propFileLoader.load(f)
        assertFalse(project.hasProperty('alpha'))
        assertFalse(project.hasProperty('file.separator'))
        assertEquals('*', System.properties['file.separator'])
        assertEquals('eins', System.properties['alpha'])
    }

    @org.junit.Test
    void traditionalSanityCheck() {
        // Can't test much specifically, but we know that the method can at
        // least be called.
        Project project = ProjectBuilder.builder().build()
        project.apply plugin: com.admc.gradle.JavaPropFilePlugin
        project.propFileLoader.traditionalPropertiesInit()
    }

    @org.junit.Test(expected=GradleException.class)
    void overwriteThrow() {
        Project project = JavaPropFilePluginTest.prepProject('alpha')

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=zwei', 'ISO-8859-1')
        project.propFileLoader.overwriteThrow = true
        project.setProperty('alpha', 'eins')
        project.propFileLoader.load(f)
    }

    @org.junit.Test
    void nochangeOverwrite() {
        Project project = JavaPropFilePluginTest.prepProject('alpha')

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=eins', 'ISO-8859-1')
        project.propFileLoader.overwriteThrow = true
        project.setProperty('alpha', 'eins')
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('alpha'))
        assertEquals('eins', project.property('alpha'))
    }

    @org.junit.Test
    void nullAssignments() {
        Project project = JavaPropFilePluginTest.prepProject(
                ['alpha', 'beta', 'gamma', 'delta'])

        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('''
alpha=
# There is trailing whitespace on next two lines:
beta()=  
  gamma()  =  
delta()=
''', 'ISO-8859-1')
        project.propFileLoader.overwriteThrow = true
        project.setProperty('alpha', 'eins')
        project.propFileLoader.load(f)
        for (p in [
            'alpha', 'beta', 'gamma', 'delta'
        ]) assertTrue(project.hasProperty(p))
        assertEquals('', project.property('alpha'))
        for (p in ['beta', 'gamma', 'delta']) assertNull(project.hasProperty(p))
    }

    @org.junit.Test
    void castedNochangeOverwrite() {
        Project project = JavaPropFilePluginTest.prepProject(
                ['alpha', 'beta', 'gamma', 'delta'])

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha(File)=eins\n(File)beta=zwei\ngamma()=', 'ISO-8859-1')
        project.propFileLoader.overwriteThrow = true
        project.propFileLoader.typeCasting = true
        project.setProperty('alpha', new File('eins'))
        project.setProperty('beta', new File('zwei'))
        project.setProperty('gamma', null)
        project.setProperty('delta', '')
        project.propFileLoader.load(f)
        for (p in ['beta', 'gamma', 'delta']) assertNull(project.hasProperty(p))
        assertEquals(new File('eins'), project.property('alpha'))
        assertEquals(new File('zwei'), project.property('beta'))
        assertNull(project.property('gamma'))
        assertEquals('', project.property('delta'))
    }

    @org.junit.Test
    void typeCasting() {
        Project project = JavaPropFilePluginTest.prepProject(
                ['aFile', 'aLong'])

        File f = JavaPropFilePluginTest.mkTestFile()
        // One using String cons. + one using valueOf methods
        f.write('aFile(File)=eins\n(Long)aLong=9764', 'ISO-8859-1')
        project.propFileLoader.overwriteThrow = true
        project.propFileLoader.typeCasting = true
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('aFile'))
        assertTrue(project.hasProperty('aLong'))
        assertEquals(new File('eins'), project.property('aFile'))
        assertEquals(Long.valueOf(9264L), project.property('aLong'))
    }

    @org.junit.Test
    void nonCastingParens() {
        Project project = JavaPropFilePluginTest.prepProject(
                ['alpha(File)x', 'x(File)alpha', 'alpha()x', 's()alpha'])

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha(File)x=eins\n(File)beta=zwei', 'ISO-8859-1')
        project.propFileLoader.overwriteThrow = true
        project.propFileLoader.typeCasting = true
        project.propFileLoader.load(f)
        for (p in ['beta', 'gamma', 'delta']) assertNull(project.hasProperty(p))
        assertTrue(project.hasProperty('alpha'))
        assertTrue(project.hasProperty('beta'))
        assertEquals(new File('eins'), project.property('alpha'))
        assertEquals(new File('zwei'), project.property('beta'))
    }

    @org.junit.Test(expected=GradleException.class)
    void malformattedNull() {
        Project project = JavaPropFilePluginTest.prepProject('alpha')

        project.propFileLoader.overwriteThrow = true
        project.propFileLoader.typeCasting = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha()=x', 'ISO-8859-1')
        project.propFileLoader.load(f)
    }

    @org.junit.Test
    void parenthesizedSysProps() {
        Project project = JavaPropFilePluginTest.prepProject(
                ['(File)systemProp.alpha', 'beta(File)', 'gama()', 'alpha',
                'beta', 'gamma', 'systemProp.alpha', 'systemProp.beta(File)',
                'systemProp.gamma()'])

        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('''
(File)systemProp.alpha=eins
systemProp.beta(File)=zwei
systemProp.gamma()=''')
        project.propFileLoader.systemPropPrefix = 'systemProp.'
        project.propFileLoader.typeCasting = true
        project.propFileLoader.load(f)
        for (p in ['alpha', 'beta', 'gamma', 'systemProp.alpha',
                'systemProp.beta(File)', 'systemProp.gamma()']) {
            if (System.properties['(File)systemProp.alpha'] != null)
                throw new IllegalStateException(
                        "System property '$p' set before our test began")
            if (project.hasProperty(p))
                throw new IllegalStateException(
                        "Project has property '$p' before our test began")
        }
        assertFalse(project.hasProperty('beta(File)'))
        assertFalse(project.hasProperty('gamma'))
        assertNull(System.properties['(File)systemProp.alpha'])

        assertTrue(project.hasProperty('(File)systemProp.alpha'))
        assertEquals('eins', project.property('(File)systemProp.alpha'))
        assertEquals('zwei', System.properties['beta(File)'])
        assertEquals('', System.properties['gamma'])
    }
}
