package ch.evolutionsoft.poker.pokernow

object ConversionConstants {
    const val TABLE_FIREFOX_10_MAX_SEAT = "Table 'Poker Now' 10-max Seat "
    const val AND_GO_ALL_IN = " and go all in"
    const val HAND_NUMBER_PREFIX_CHAR = '#'
    const val FORWARD_SLASH = "/"
    const val ONE_OR_MORE_CHARACTERS = ".+"
    const val ONE_OR_MORE_DIGITS_REGEX = "\\d+(\\.\\d{1,2})?"
    const val ONE_OR_MORE_DIGITS_GROUP_REGEX = "(\\d+)(\\.\\d{1,2})?"
    const val ONE_OR_MORE_DIGITS_AND_CHARS_REGEX = "(\\d+)(\\.\\d{1,2})?( and go all in)?\".+"
    const val POKERNOW_NEWLINE = "\n"
    const val DOUBLE_POINT = ':'
    const val FIRST_REGEX_GROUP_MATCH = "$1"
    const val FIRST_AND_SECOND_REGEX_GROUP_MATCH = "$1$2"
    const val COMMA_CHAR = ','
    const val CLOSED_BRACKET = "]"
    const val OPEN_BRACKET = "["
    const val AMOUNT_DOUBLE_FORMAT = "%.4f"
    const val DOLLAR_SIGN = "$"
    const val CURRENCY_AMOUNT_REGEX = "\\$" + ONE_OR_MORE_DIGITS_REGEX
    const val ESCAPED_CURRENCY = "\\$"
    const val SMALL_CAPACITY = 9
}
