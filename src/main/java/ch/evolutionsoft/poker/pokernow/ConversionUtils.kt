package ch.evolutionsoft.poker.pokernow

import org.apache.commons.lang3.StringUtils

object ConversionUtils {
    @JvmStatic
    fun readIdPrefixFromDatetime(handHistory: String): Long {
        val handHistoryLine = handHistory.substring(0, handHistory.indexOf(ConversionConstants.POKERNOW_NEWLINE))
        val datetime = handHistoryLine.replace(PokerNowConstants.POKERNOW_DATETIME_PATTERN.toRegex(), ConversionConstants.FIRST_REGEX_GROUP_MATCH)
        var datetimeDigits = datetime.replace("-", StringUtils.EMPTY)
        datetimeDigits = datetimeDigits.replace(PokerNowConstants.TIME_PREFIX, StringUtils.EMPTY)
        datetimeDigits = datetimeDigits.replace(ConversionConstants.DOUBLE_POINT.toString(), StringUtils.EMPTY)
        return datetimeDigits.toLong()
    }

    @JvmStatic
    fun readSmallBlind(handHistory: String, lastSmallBlind: Double): Double {
        var tempHandHistory = handHistory
        tempHandHistory = tempHandHistory.replace(ConversionConstants.AND_GO_ALL_IN, StringUtils.EMPTY)
        val indexOfFirstSmallBlindPrefix = tempHandHistory.indexOf(PokerNowConstants.SMALL_BLIND_PREFIX)
        val indexOfNextNewLine = tempHandHistory.indexOf(ConversionConstants.POKERNOW_NEWLINE, indexOfFirstSmallBlindPrefix)
        if (indexOfFirstSmallBlindPrefix < 0) {
            return lastSmallBlind
        }
        val smallBlindLinePart = tempHandHistory.substring(indexOfFirstSmallBlindPrefix, indexOfNextNewLine)
        val smallBlindAmount = smallBlindLinePart.replaceFirst((PokerNowConstants.SMALL_BLIND_PREFIX + ConversionConstants.ONE_OR_MORE_DIGITS_AND_CHARS_REGEX).toRegex(), ConversionConstants.FIRST_AND_SECOND_REGEX_GROUP_MATCH)
        return smallBlindAmount.toDouble()
    }

    @JvmStatic
    fun readBigBlind(handHistory: String): Double {
        var tempHandHistory = handHistory
        tempHandHistory = tempHandHistory.replace(ConversionConstants.AND_GO_ALL_IN + StringUtils.SPACE, StringUtils.EMPTY)
        val indexOfFirstBigBlindPrefix = tempHandHistory.indexOf(PokerNowConstants.BIG_BLIND_PREFIX)
        val indexOfNextNewLine = tempHandHistory.indexOf(ConversionConstants.POKERNOW_NEWLINE, indexOfFirstBigBlindPrefix)
        val bigBlindLinePart = tempHandHistory.substring(indexOfFirstBigBlindPrefix, indexOfNextNewLine)
        val bigBlindAmount = bigBlindLinePart.replaceFirst((PokerNowConstants.BIG_BLIND_PREFIX + ConversionConstants.ONE_OR_MORE_DIGITS_AND_CHARS_REGEX).toRegex(), ConversionConstants.FIRST_AND_SECOND_REGEX_GROUP_MATCH)
        return bigBlindAmount.toDouble()
    }

    @JvmStatic
    fun stripDateAndEntryOrderFromCsv(gameAction: String): String {
        return gameAction.substring(0, gameAction.indexOf(PokerNowConstants.SINGLE_QUOTE_CHAR))
    }

    @JvmStatic
    fun replaceColorsAndTens(showdownAction: String): String {
        var tempShowdownAction = showdownAction
        tempShowdownAction = tempShowdownAction.replace(ConversionConstants.COMMA_CHAR.toString() + StringUtils.SPACE, StringUtils.SPACE)
        tempShowdownAction = tempShowdownAction.replace(PokerNowConstants.SPADES, PokerStarsConstants.SPADES)
        tempShowdownAction = tempShowdownAction.replace(PokerNowConstants.HEARTS, PokerStarsConstants.HEARTS)
        tempShowdownAction = tempShowdownAction.replace(PokerNowConstants.CLUBS, PokerStarsConstants.CLUBS)
        tempShowdownAction = tempShowdownAction.replace(PokerNowConstants.DIAMONDS, PokerStarsConstants.DIAMONDS)
        tempShowdownAction = replaceTens(tempShowdownAction)
        return tempShowdownAction
    }

    @JvmStatic
    fun replaceTens(gameAction: String): String {
        var tempGameAction = gameAction
        tempGameAction = tempGameAction.replace("10s", "Ts")
        tempGameAction = tempGameAction.replace("10h", "Th")
        tempGameAction = tempGameAction.replace("10c", "Tc")
        tempGameAction = tempGameAction.replace("10d", "Td")
        return tempGameAction
    }
}
