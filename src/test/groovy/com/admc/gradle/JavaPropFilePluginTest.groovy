package com.admc.gradle

import org.gradle.api.Project
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import static org.junit.Assert.*

class JavaPropFilePluginTest {
    private static File mkTestFile() {
        File newFile = File.createTempFile(getClass().simpleName, '.properties')
        newFile.deleteOnExit()
        return newFile
    }

    private static Project prepProject(String... checkProps) {
        Project proj = ProjectBuilder.builder().build()
        proj.apply plugin: com.admc.gradle.JavaPropFilePlugin

        checkProps.each {
            assert !proj.hasProperty(it):
                "Gradle project has property '$it' set before test begins"
            System.properties.remove(it)
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
        project.propFileLoader.systemPropPrefix = 'sp|'
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('me=I am ${sp|user.name}', 'ISO-8859-1')
        project.propFileLoader.load(f)

        assertTrue(project.hasProperty('me'))
        assertEquals( 'I am ' + System.properties['user.name'],
                project.property('me'))
    }

    @org.junit.Test
    void refNest() {
        Project project = JavaPropFilePluginTest.prepProject('alpha', 'beta')

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

    @org.junit.Test
    void changeToNull() {
        Project project = JavaPropFilePluginTest.prepProject('aNull')
        project.setProperty('aNull', (String) null)

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('aNull=one', 'ISO-8859-1')
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('aNull'))
        assertEquals('one', project.property('aNull'))
    }

    @org.junit.Test(expected=GradleException.class)
    void unsetLoneThrow() {
        Project project =
                JavaPropFilePluginTest.prepProject('notset', 'alpha')

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
    void unsetLoneEmpties() {
        Project project = JavaPropFilePluginTest.prepProject('alpha', 'beta')

        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=${notset}\nsp|beta=pre${alsoNotSet}post', 'ISO-8859-1')
        project.propFileLoader.unsatisfiedRefBehavior =
                JavaPropFile.Behavior.EMPTY
        project.propFileLoader.systemPropPrefix = 'sp|'
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('alpha'))
        assertFalse(project.hasProperty('beta'))
        assertFalse(System.properties.containsKey('alpha'))
        assertTrue(System.properties.containsKey('beta'))
        assertEquals('', project.property('alpha'))
        assertEquals('prepost', System.properties['beta'])
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
    void deepRefNest() {
        Project project = JavaPropFilePluginTest.prepProject(
                'bottom1', 'mid2', 'mid3a', 'mid3b', 'top4')

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
        Project project = JavaPropFilePluginTest.prepProject('alpha', 'beta')

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
        Project project = JavaPropFilePluginTest.prepProject('alpha', 'beta')

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
        Project project = prepProject(
            'alpha', 'sys|file.separator', 'sys|alpha'
        )
        assert !project.hasProperty('file.separator'):
            '''Project has property 'file.separator' set before we start test'''
        
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('sys|alpha=eins\nsys|file.separator=*')
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
                'alpha', 'beta', 'gamma', 'delta')

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
        project.propFileLoader.typeCasting = true
        project.propFileLoader.load(f)
        ['alpha', 'beta', 'gamma', 'delta'].each {
            assertTrue("Missing property '$it'", project.hasProperty(it))
        }
        assertEquals('', project.property('alpha'))
        ['beta', 'gamma', 'delta'].each {
            assertNull("Non-null property '$it'", project.property(it))
        }
    }

    @org.junit.Test
    void castedNochangeOverwrite() {
        Project project = JavaPropFilePluginTest.prepProject(
                'alpha', 'beta', 'gamma', 'delta')

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha(File)=eins\n(File)beta=zwei\ngamma()=', 'ISO-8859-1')
        project.propFileLoader.overwriteThrow = true
        project.propFileLoader.typeCasting = true
        project.setProperty('alpha', new File('eins'))
        project.setProperty('beta', new File('zwei'))
        project.setProperty('gamma', null)
        project.setProperty('delta', '')
        project.propFileLoader.load(f)
        ['alpha', 'beta', 'gamma', 'delta'].each {
            assertTrue("Missing property '$it'", project.hasProperty(it))
        }
        assertEquals(new File('eins'), project.property('alpha'))
        assertEquals(new File('zwei'), project.property('beta'))
        assertNull(project.property('gamma'))
        assertEquals('', project.property('delta'))
    }

    @org.junit.Test
    void typeCasting() {
        Project project = JavaPropFilePluginTest.prepProject('aFile', 'aLong')

        File f = JavaPropFilePluginTest.mkTestFile()
        // One using String cons. + one using valueOf methods
        f.write('aFile(File)=eins\n(Long)aLong=9764', 'ISO-8859-1')
        project.propFileLoader.overwriteThrow = true
        project.propFileLoader.typeCasting = true
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('aFile'))
        assertTrue(project.hasProperty('aLong'))
        assertEquals(new File('eins'), project.property('aFile'))
        assertEquals(Long.valueOf(9764L), project.property('aLong'))
    }

    @org.junit.Test
    void nonCastingParens() {
        Project project = JavaPropFilePluginTest.prepProject(
                'alpha(File)x', 'x(File)beta', 'alpha', 'beta',
                'alpha()x', 's()alpha')

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha(File)x=eins\nx(File)beta=zwei', 'ISO-8859-1')
        project.propFileLoader.overwriteThrow = true
        project.propFileLoader.typeCasting = true
        project.propFileLoader.load(f)
        ['alpha', 'beta'].each {
            assertFalse("Missing property '$it'", project.hasProperty(it))
        }
        ['alpha(File)x', 'x(File)beta'].each {
            assertTrue("Present property '$it'", project.hasProperty(it))
        }
        assertEquals('eins', project.property('alpha(File)x'))
        assertEquals('zwei', project.property('x(File)beta'))
    }

    @org.junit.Test(expected=GradleException.class)
    void overCasted1() {
        Project project = JavaPropFilePluginTest.prepProject(
                'epsilon()', '(File)epsilon()')

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('(File)epsilon()=sechs', 'ISO-8859-1')
        project.propFileLoader.overwriteThrow = true
        project.propFileLoader.typeCasting = true
        project.propFileLoader.load(f)
    }

    @org.junit.Test(expected=GradleException.class)
    void overCasted2() {
        Project project =
                JavaPropFilePluginTest.prepProject('(File)delta(junk)')

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('(File)delta(junk)=vier', 'ISO-8859-1')
        project.propFileLoader.overwriteThrow = true
        project.propFileLoader.typeCasting = true
        project.propFileLoader.load(f)
    }

    @org.junit.Test(expected=GradleException.class)
    void overCasted3() {
        Project project =
                JavaPropFilePluginTest.prepProject('gamma', '(gamma)')

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('(File)delta(junk)=vier', 'ISO-8859-1')
        project.propFileLoader.overwriteThrow = true
        project.propFileLoader.typeCasting = true
        project.propFileLoader.load(f)
    }

    @org.junit.Test(expected=GradleException.class)
    void emptyCast() {
        Project project = JavaPropFilePluginTest.prepProject('()')

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('()=', 'ISO-8859-1')
        project.propFileLoader.overwriteThrow = true
        project.propFileLoader.typeCasting = true
        project.propFileLoader.load(f)
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

    @org.junit.Test(expected=GradleException.class)
    void conflictingAssignments() {
        Project project = JavaPropFilePluginTest.prepProject('alpha', 'beta')
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=1\nalpha=2', 'ISO-8859-1')
        project.propFileLoader.load(f)
    }

    @org.junit.Test(expected=GradleException.class)
    void missingCastingClass() {
        Project project = JavaPropFilePluginTest.prepProject('alpha')

        project.propFileLoader.overwriteThrow = true
        project.propFileLoader.typeCasting = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha(NoSuchClass)=x', 'ISO-8859-1')
        project.propFileLoader.load(f)
    }

    @org.junit.Test
    void parenthesizedSysProps() {
        Project project = JavaPropFilePluginTest.prepProject(
                '(File)systemProp|alpha', 'beta(File)', 'gamma()', 'alpha',
                'beta', 'gamma', 'systemProp|alpha', 'systemProp|beta(File)',
                'systemProp|gamma()')

        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('''
(File)systemProp|alpha=eins
systemProp|beta(File)=zwei
systemProp|gamma()=
''')
        project.propFileLoader.systemPropPrefix = 'systemProp|'
        project.propFileLoader.typeCasting = true
        project.propFileLoader.load(f)
        ['alpha', 'beta', 'gamma', 'systemProp|alpha', '(File)systemProp|alpha',
                'systemProp|beta(File)', 'systemProp|gamma()'].each {
            assertFalse("System property '$it' is set",
                    System.properties.containsKey(it))
                
        }
        ['alpha', 'beta', 'gamma', 'beta(File)', 'gamma()',
                'systemProp|beta(File)', 'systemProp|gamma()'].each {
            assertFalse("Project has property '$it'", project.hasProperty(it))
        }

        assertTrue(project.hasProperty('systemProp|alpha'))
        assertTrue(System.properties.containsKey('beta(File)'))
        assertTrue(System.properties.containsKey('gamma()'))
        assertEquals(new File('eins'), project.property('systemProp|alpha'))
        assertEquals('zwei', System.properties['beta(File)'])
        assertEquals('', System.properties['gamma()'])
    }

    @org.junit.Test
    void escapeRef() {
        Project project = JavaPropFilePluginTest.prepProject('alpha')

        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=one\\\\${escaped}two')
        project.propFileLoader.typeCasting = true
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('alpha'))
        assertEquals('one${escaped}two', project.property('alpha'))
    }

    @org.junit.Test
    void escapedDotRef() {
        Project project = JavaPropFilePluginTest.prepProject('al.pha', 'beta')

        project.setProperty('al.pha', 'one')
        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('beta =pre${al\\\\.pha}post')
        project.propFileLoader.typeCasting = true
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('al.pha'))
        assertTrue(project.hasProperty('beta'))
        assertEquals('preonepost', project.property('beta'))
    }

    @org.junit.Test
    void escapeNameDollar() {
        Project project = JavaPropFilePluginTest.prepProject('al$pha')

        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('al\\\\$pha=one')
        project.propFileLoader.typeCasting = true
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('al$pha'))
        assertEquals('one', project.property('al$pha'))
    }

    @org.junit.Test
    void escapedDotSet() {
        Project project = JavaPropFilePluginTest.prepProject('al.pha')

        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('al\\\\.pha =one')
        project.propFileLoader.typeCasting = true
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('al.pha'))
        assertEquals('one', project.property('al.pha'))
    }

    @org.junit.Test
    void escapeNameOpenParen() {
        Project project = JavaPropFilePluginTest.prepProject('(al)pha')

        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('\\\\(al)pha=one')
        project.propFileLoader.typeCasting = true
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('(al)pha'))
        assertEquals('one', project.property('(al)pha'))
    }

    @org.junit.Test
    void escapeNameCloseParen() {
        Project project = JavaPropFilePluginTest.prepProject('(al)pha')

        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('(al\\\\)pha=one')
        project.propFileLoader.typeCasting = true
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('(al)pha'))
        assertEquals('one', project.property('(al)pha'))
    }

    @org.junit.Test(expected=GradleException.class)
    void noDefer() {
        Project project = JavaPropFilePluginTest.prepProject()

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('mockBean$str2=val')
        project.propFileLoader.defer = false
        project.propFileLoader.load(f)
    }

    @org.junit.Test
    void deferredExtObjAssignment() {
        Project project = JavaPropFilePluginTest.prepProject()

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('mockBean$str2=val')
        project.propFileLoader.load(f)
        assertEquals(1, project.propFileLoader.deferredExtensionProps.size())
        project.apply plugin: com.admc.gradle.MockPlugin
        //assertNull(project.mockBean.str2)
        // If executeDeferrals not invoked via callback, do:
        //project.propFileLoader.executeDeferrals()
        assertEquals('val', project.mockBean.str2)
        assertEquals(0, project.propFileLoader.deferredExtensionProps.size())
    }

    @org.junit.Test
    void deferredExtObjNestAssignment() {
        Project project = JavaPropFilePluginTest.prepProject()

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('''
mockBean$tHolder2(com.admc.gradle.MockBean$ThreadHolder) =New Thread Name
mockBean$tHolder2.heldThread.name =Renamed Thread
        ''')
        project.propFileLoader.typeCasting = true
        project.propFileLoader.load(f)
        assertEquals(1, project.propFileLoader.deferredExtensionProps.size())
        project.apply plugin: com.admc.gradle.MockPlugin
        assertEquals(0, project.propFileLoader.deferredExtensionProps.size())
        //assertEquals('name:New Thread Name',
        assertEquals('Renamed Thread', project.mockBean.tHolder2.heldThread.name)
    }

    @org.junit.Test
    void extObjAssignment() {
        Project project = JavaPropFilePluginTest.prepProject()

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('mockBean$str2=val')
        project.apply plugin: com.admc.gradle.MockPlugin
        assertNull(project.mockBean.str2)
        project.propFileLoader.load(f)
        assertEquals('val', project.mockBean.str2)
    }

    @org.junit.Test
    void extObjRef() {
        Project project = JavaPropFilePluginTest.prepProject('alpha')

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=pre${mockBean$str1}post')
        project.apply plugin: com.admc.gradle.MockPlugin
        project.mockBean.assignSome()
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('alpha'))
        assertEquals('preonepost', project.property('alpha'))
    }

    @org.junit.Test
    void objNestRef() {
        Project project = JavaPropFilePluginTest.prepProject('alpha')
        project.propFileLoader.overwriteThrow = true

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=pre${mockBean$str1}post')
        project.apply plugin: com.admc.gradle.MockPlugin
        project.mockBean.assignSome()
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('alpha'))
        assertEquals('preonepost', project.property('alpha'))
    }

    @org.junit.Test
    void castColObjNestRef() {
        Project project = JavaPropFilePluginTest.prepProject('alpha')

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=pre${mockBean$strList}post')
        project.apply plugin: com.admc.gradle.MockPlugin
        project.mockBean.assignSome()
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('alpha'))
        assertEquals('pre' + ['ONE', 'TWO', 'THREE'].toString() + 'post',
                project.property('alpha'))
    }

    @org.junit.Test
    void ObjDeepNestRef() {
        Project project = JavaPropFilePluginTest.prepProject('alpha')
        project.propFileLoader.overwriteThrow = true

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha =pre${mockBean$tHolder1.heldThread.name}post')
        project.apply plugin: com.admc.gradle.MockPlugin
        project.mockBean.assignSome()
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('alpha'))
        assertEquals('prename:unopost', project.property('alpha'))
    }


    @org.junit.Test
    void castColObjNestSet() {
        Project project = JavaPropFilePluginTest.prepProject()
        project.propFileLoader.typeCasting = true

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('mockBean$intList(Integer[\\\\|]ArrayList)=91|72|101')
        project.apply plugin: com.admc.gradle.MockPlugin
        project.propFileLoader.load(f)
        assertEquals([91, 72, 101], project.mockBean.intList)
    }

    @org.junit.Test
    void objDeepNestSet() {
        Project project = JavaPropFilePluginTest.prepProject()

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('mockBean$tHolder1.heldThread.name =New Thread Name')
        project.apply plugin: com.admc.gradle.MockPlugin
        project.mockBean.assignSome()
        project.propFileLoader.load(f)
        assertEquals('New Thread Name',
                project.mockBean.tHolder1.heldThread.name)
    }

    @org.junit.Test
    void arrayCast() {
        Project project = JavaPropFilePluginTest.prepProject('alpha')

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha(Integer[\\ ])=94 3 12')
        project.propFileLoader.typeCasting = true
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('alpha'))
        assertNotNull(project.hasProperty('alpha'))
        assertEquals('[Ljava.lang.Integer;',
                project.property('alpha').class.name)
        assertArrayEquals((Integer[]) [94, 3, 12], project.property('alpha'))
    }

    @org.junit.Test
    void listCast() {
        Project project = JavaPropFilePluginTest.prepProject('alpha')

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha(Integer[\\ ]ArrayList)=94 3 12')
        project.propFileLoader.typeCasting = true
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('alpha'))
        assertNotNull(project.hasProperty('alpha'))
        assertEquals(ArrayList.class, project.property('alpha').class)
        assertEquals([94, 3, 12] as ArrayList, project.property('alpha'))
    }

    @org.junit.Test
    void setCast() {
        Project project = JavaPropFilePluginTest.prepProject('alpha')

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha(Integer[\\ ]HashSet)=94 3 12')
        project.propFileLoader.typeCasting = true
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('alpha'))
        assertNotNull(project.hasProperty('alpha'))
        assertEquals(HashSet.class, project.property('alpha').class)
        assertEquals([94, 3, 12] as HashSet, project.property('alpha'))
    }

    @org.junit.Test
    void modifyExistingSeeChange() {
        Project project = JavaPropFilePluginTest.prepProject(
                'alpha', 'beta', 'gamma')

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('beta=two${alpha}\nalpha=eins\ngamma=three${alpha}\n')
        project.setProperty('alpha', 'one')
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('alpha'))
        assertTrue(project.hasProperty('beta'))
        assertTrue(project.hasProperty('gamma'))
        assertEquals('eins', project.property('alpha'))
        assertEquals('twoone', project.property('beta'))
        assertEquals('threeeins', project.property('gamma'))
    }

    @org.junit.Test
    void modifyExistingNoSeeChange() {
        Project project = JavaPropFilePluginTest.prepProject('alpha', 'beta')

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=eins\nbeta=two${alpha}\n')
        project.setProperty('alpha', 'one')
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('alpha'))
        assertTrue(project.hasProperty('beta'))
        assertEquals('eins', project.property('alpha'))
        assertEquals('twoeins', project.property('beta'))
    }

    @org.junit.Test
    void projCastObjNesting() {
        Project project = JavaPropFilePluginTest.prepProject('t1')
        project.propFileLoader.typeCasting = true

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('t1(Thread)=one\nt1.name =two')
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('t1'))
        assertEquals('two', project.t1.name)
    }

    @org.junit.Test
    void nonDerefDot() {
        Project project = JavaPropFilePluginTest.prepProject(
            'alpha.beta.gamma', 'delta.epsilon.mu', 'nu')
        project.propFileLoader.typeCasting = true
        project.setProperty('alpha.beta.gamma', 'eins')

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('delta.epsilon.mu=zwei\nnu=pre${alpha.beta.gamma}post')
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('delta.epsilon.mu'))
        assertTrue(project.hasProperty('nu'))
        assertEquals('zwei', project.property('delta.epsilon.mu'))
        assertEquals('preeinspost', project.property('nu'))
    }

    @org.junit.Test
    void targetedPropertiesWithDeferrals() {
        /* Also tests that sys prop settings in the target file work */
        Project project = JavaPropFilePluginTest.prepProject('aSysProp')

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('''
tHolder2(com.admc.gradle.MockBean$ThreadHolder) =New Thread Name
tHolder2.heldThread.name =Renamed Thread
sp|aSysProp=werd
        ''')
        project.propFileLoader.typeCasting = true
        project.propFileLoader.systemPropPrefix = 'sp|'
        project.propFileLoader.load(f, 'mockBean')
        assertEquals(1, project.propFileLoader.deferredExtensionProps.size())
        project.apply plugin: com.admc.gradle.MockPlugin
        assertEquals(0, project.propFileLoader.deferredExtensionProps.size())
        assertEquals('Renamed Thread', project.mockBean.tHolder2.heldThread.name)
        assertTrue(System.properties.containsKey('aSysProp'))
        assertEquals('werd', System.properties['aSysProp'])
    }
}
