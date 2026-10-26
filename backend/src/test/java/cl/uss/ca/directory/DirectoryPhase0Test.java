package cl.uss.ca.directory;

public final class DirectoryPhase0Test {
    private DirectoryPhase0Test() {
    }

    public static void main(String[] args) throws Exception {
        DirectoryImportIdentityTest.main(args);
        DirectoryImportPlannerTest.main(args);
        DirectoryExcelParserTest.main(args);
        DirectoryRutCryptoTest.main(args);
        DirectoryPresencePolicyTest.main(args);
        DirectoryImportRecoveryTest.main(args);
        DirectorySensitiveSurfaceTest.main(args);
        System.out.println("DirectoryPhase0Test OK");
    }
}
