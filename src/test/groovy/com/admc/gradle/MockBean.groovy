package com.admc.gradle

class MockBean {
    static class ThreadHolder {
        Thread heldThread
        String aString
        ThreadHolder(String aString) {
             this.aString = aString
             heldThread = new Thread('name:' + aString)
        }
    }

    ThreadHolder tHolder1, tHolder2
    String autoStr1, autoStr2, autoStr3, autoStr4
    private String str1, str2, str3, str4
    File autoFile1, autoFile2
    private File file1, file2
    Collection strCol, intCol
    Integer int1, int2
    List strList, intList
    Set strSet, intSet
    String[] strArr
    Integer[] intArr

    String getStr1() { return str1 }
    String getStr2() { return str2 }
    String getStr3() { return str3 }
    String getStr4() { return str4 }
    String getFile1() { return file1 }
    String getFile2() { return file2 }

    void setStr1(String str1) { this.str1 = str1 }
    void setStr2(String str2) { this.str2 = str2 }
    void setStr3(String str3) { this.str3 = str3 }
    void setStr4(String str4) { this.str4 = str4 }
    void setFile1(String file1) { this.file1 = file1 }
    void setFile2(String file2) { this.file2 = file2 }

    void assignSome() {
        str1 = 'one'; autoStr1 = 'auto one'
        int1 = Integer.valueOf(316);
        file1 = new File('file1.txt')
        autoFile1 = new File('/auto/file01.txt')
        strCol = ['one', 'two', 'three']
        strSet = ['One', 'Two', 'Three'] as HashSet
        strList = ['ONE', 'TWO', 'THREE'] as ArrayList
        strArr = ['oNe', 'tWo', 'tHree'] as String[]
        tHolder1 = new ThreadHolder('uno')
    }

    String toString() {
        StringBuilder sb = new StringBuilder()
        sb.append('strCol')
        if (strCol != null) sb.append(', a ').append(strCol.class.name)
        sb.append('=').append(strCol).append('\n')
        .append('intCol')
        if (intCol != null) sb.append(', a ').append(intCol.class.name)
        sb.append('=').append(intCol).append('\n')
        .append('strSet')
        if (strSet != null) sb.append(', a ').append(strSet.class.name)
        sb.append('=').append(strSet).append('\n')
        .append('intSet')
        if (intSet != null) sb.append(', a ').append(intSet.class.name)
        sb.append('=').append(intSet).append('\n')
        .append('strSet')
        if (strList != null) sb.append(', a ').append(strList.class.name)
        sb.append('=').append(strList).append('\n')
        .append('intList')
        if (intList != null) sb.append(', a ').append(intList.class.name)
        sb.append('=').append(intList).append('\n')
        .append('strArr')
        if (strArr != null) sb.append(', a ').append(strArr.class.name)
        sb.append('=').append((strArr== null)
                              ? '<NULL>' : Arrays.toString(strArr))
        .append('\n')
        .append('intArr')
        if (intArr != null) sb.append(', a ').append(intArr.class.name)
        sb.append('=').append((intArr== null)
                              ? '<NULL>' : Arrays.toString(intArr))
        .append('\n')

        sb.append('\nfile1=').append(file1)
        .append('\nfile2=').append(file2)
        .append('\nautoFile1=').append(autoFile1)
        .append('\nautoFile2=').append(autoFile2)
        .append('\nint 1 & 2:  ').append(int1).append(' | ').append(int2)
        .append('\nstr 1 to 4:  (').append(str1).append('), (').append(str2)
        .append('), (').append(str3).append('), (').append(str4).append(')')
        .append('\nautoStr 1 to 4:  (').append(autoStr1).append('), (')
        .append(autoStr2).append('), (').append(autoStr3).append('), (')
        .append(autoStr4).append(')')
        return sb.toString()
    }
}
