ruleset {
    // rulesets/braces.xml
    ElseBlockBraces
    ForStatementBraces
    IfStatementBraces
    WhileStatementBraces

    // rulesets/convention.xml
    ConfusingTernary
    CouldBeElvis
    LongLiteralWithLowerCaseL
    NoTabCharacter
    ParameterReassignment
    TernaryCouldBeElvis
    TrailingComma

    // rulesets/formatting.xml
    BlockEndsWithBlankLine
    BlockStartsWithBlankLine
    BracesForClass
    BracesForForLoop
    BracesForIfElse
    BracesForMethod
    BracesForTryCatchFinally
    ClosureStatementOnOpeningLineOfMultipleLineClosure
    FileEndsWithoutNewline
    // Indentation
    LineLength { length = 100 }
    MissingBlankLineAfterImports
    MissingBlankLineAfterPackage
    SpaceAfterCatch
    SpaceAfterClosingBrace
    SpaceAfterComma
    SpaceAfterFor
    SpaceAfterIf
    SpaceAfterOpeningBrace
    SpaceAfterSemicolon
    SpaceAfterSwitch
    SpaceAfterWhile
    SpaceAroundClosureArrow
    // SpaceAroundMapEntryColon { characterAfterColonRegex = /\s/ }
    SpaceAroundOperator
    SpaceBeforeClosingBrace
    SpaceBeforeOpeningBrace
    TrailingWhitespace

    // rulesets/imports.xml
    DuplicateImport
    ImportFromSamePackage
    MisorderedStaticImports { comesBefore = false }
    NoWildcardImports
    UnnecessaryGroovyImport
    UnusedImport

    // rulesets/naming.xml
    FieldName
    PropertyName
    VariableName
}
