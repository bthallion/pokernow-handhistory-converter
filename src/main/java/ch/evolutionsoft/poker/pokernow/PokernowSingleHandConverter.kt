package ch.evolutionsoft.poker.pokernow

import ch.evolutionsoft.poker.pokernow.ConversionUtils.readBigBlind
import ch.evolutionsoft.poker.pokernow.ConversionUtils.readSmallBlind
import ch.evolutionsoft.poker.pokernow.ConversionUtils.replaceColorsAndTens
import ch.evolutionsoft.poker.pokernow.ConversionUtils.replaceTens
import ch.evolutionsoft.poker.pokernow.ConversionUtils.stripDateAndEntryOrderFromCsv
import ch.evolutionsoft.poker.pokernow.PropertyHelper.readConvertYourHoleCards
import ch.evolutionsoft.poker.pokernow.PropertyHelper.readYourUniqueName
import org.apache.commons.lang3.StringUtils
import org.apache.logging.log4j.LogManager
import java.io.BufferedReader
import java.io.IOException
import java.io.StringReader
import java.util.regex.Pattern
import kotlin.math.roundToInt

class PokerNowSingleHandConverter {
    private var lastButtonPlayerName = StringUtils.EMPTY
    private var lastButtonPlayerSeat = StringUtils.EMPTY
    private var smallBlindAmount = 1.0
    private var bigBlindAmount = 1.0
    private var straddleAmount = 2.0
    private var latestBetTotalAmount = 1.0
    private var lastRaiseAmountByPlayer: MutableMap<String, Double> = HashMap()
    var isReadYourHoleCards = true
    private var yourHoleCards = StringUtils.EMPTY

    @JvmField
    var yourUniqueName = StringUtils.EMPTY
    private var yourSeat = StringUtils.EMPTY

    constructor() {
        isReadYourHoleCards = readConvertYourHoleCards()
        yourUniqueName = readYourUniqueName()
        handConversionLog.info("Using readYourHoleCards {}", isReadYourHoleCards)
        handConversionLog.info("Using yourUniqueName {}", yourUniqueName)
    }

    constructor(readYourHoleCards: Boolean, yourUniqueName: String) {
        isReadYourHoleCards = readYourHoleCards
        this.yourUniqueName = yourUniqueName
        handConversionLog.info("Using readYourHoleCards {}", isReadYourHoleCards)
        handConversionLog.info("Using yourUniqueName {}", this.yourUniqueName)
    }

    @Throws(IOException::class)
    fun convertSingleHand(singleHandHistoryBase: String?, handIdPrefix: Long): String? {
        smallBlindAmount = readSmallBlind(singleHandHistoryBase!!, smallBlindAmount)
        bigBlindAmount = readBigBlind(singleHandHistoryBase)
        straddleAmount = 2 * bigBlindAmount
        latestBetTotalAmount = bigBlindAmount
        lastRaiseAmountByPlayer.clear()
        val bufferedReader = BufferedReader(StringReader(singleHandHistoryBase))
        var singleHandHistoryLine = bufferedReader.readLine()
        val handNumber = readHandNumber(singleHandHistoryLine)
        val gameTypeGameIdPattern = Pattern.compile(
            "hand " + ConversionConstants.HAND_NUMBER_PREFIX_CHAR + "\\d+ (\\(id: \\w+\\))?\\s+\\(([^)]+)\\)"
        )
        val m = gameTypeGameIdPattern.matcher(singleHandHistoryLine)
        var gameType = "UndefinedGameType"
        if (m.find()) {
            gameType = m.group(PokerNowConstants.GAME_TYPE_GROUP) + " "
        } else {
            handConversionLog.warn("Gametype could not be parsed from history line: \"{}\"", singleHandHistoryLine)
        }
        var timeString = singleHandHistoryLine!!.substring(
            singleHandHistoryLine.indexOf(PokerNowConstants.DOUBLE_QUOTE + ConversionConstants.COMMA_CHAR) + 2,
            singleHandHistoryLine.lastIndexOf('.')
        )
        timeString = timeString.replace("-", ConversionConstants.FORWARD_SLASH)
        timeString = timeString.replace(PokerNowConstants.TIME_PREFIX, StringUtils.SPACE)
        val pokerstarsHandLine1: String =
            PokerStarsConstants.POKER_STARS_HAND + handIdPrefix + handNumber + ConversionConstants.DOUBLE_POINT + StringUtils.SPACE +
                    gameType + '(' + PokerStarsConstants.DOLLAR_CHAR + smallBlindAmount + ConversionConstants.FORWARD_SLASH +
                    PokerStarsConstants.DOLLAR_CHAR + bigBlindAmount + " USD" + ')' + " - " + timeString + " CET" + System.lineSeparator()
        var convertedSingleHandHistory: String? = pokerstarsHandLine1
        var nextSingleHandHistoryLine = bufferedReader.readLine()
        while (nextSingleHandHistoryLine.contains("joined")) {
            nextSingleHandHistoryLine = bufferedReader.readLine()
        }
        val indexOfDealerPrefix: Int = singleHandHistoryLine.indexOf(PokerNowConstants.BUTTON_PREFIX)
        val buttonPlayerName: String
        if (indexOfDealerPrefix >= 0) {
            buttonPlayerName = singleHandHistoryLine.substring(
                indexOfDealerPrefix + PokerNowConstants.BUTTON_PREFIX_LENGTH,
                singleHandHistoryLine.indexOf(')', indexOfDealerPrefix)
            )
            val indexOfDealerSeat = nextSingleHandHistoryLine.indexOf(buttonPlayerName) - 3
            val buttonPlayerSeat =
                nextSingleHandHistoryLine.substring(indexOfDealerSeat, indexOfDealerSeat + 3).trim { it <= ' ' }
            lastButtonPlayerSeat = buttonPlayerSeat
            lastButtonPlayerName = buttonPlayerName
            convertedSingleHandHistory += ConversionConstants.TABLE_FIREFOX_10_MAX_SEAT + buttonPlayerSeat + " is the button" + System.lineSeparator()
        } else {
            convertedSingleHandHistory += ConversionConstants.TABLE_FIREFOX_10_MAX_SEAT + lastButtonPlayerSeat + " is the button" + System.lineSeparator()
        }
        var playerSummary = nextSingleHandHistoryLine.substring(
            nextSingleHandHistoryLine.indexOf(": #") + 2,
            nextSingleHandHistoryLine.indexOf(ConversionConstants.COMMA_CHAR) - 1
        )
        playerSummary = playerSummary.replace(" | ", System.lineSeparator())
        playerSummary = playerSummary.replace("#", "Seat ")
        playerSummary = playerSummary.replace("Seat (\\d+(\\.\\d{2})?)".toRegex(), "Seat $1:")
        playerSummary = playerSummary.replace("(", "($")
        playerSummary = playerSummary.replace(")", " in Chips)")
        convertedSingleHandHistory += playerSummary + System.lineSeparator()
        singleHandHistoryLine = bufferedReader.readLine()
        yourHoleCards = StringUtils.EMPTY
        if (isReadYourHoleCards && playerSummary.contains(yourUniqueName)
            && singleHandHistoryLine.contains(PokerNowConstants.HOLE_CARD_PREFIX)
        ) {
            val indexOfYourPlayerKey = playerSummary.indexOf(yourUniqueName)
            var yourSeatNumber = playerSummary.substring(indexOfYourPlayerKey - 4, indexOfYourPlayerKey - 2)
            yourSeatNumber = yourSeatNumber.replace(StringUtils.SPACE, "")
            yourSeat = "Seat $yourSeatNumber"
            val indexOfHoleCardsStart: Int =
                singleHandHistoryLine.indexOf(PokerNowConstants.HOLE_CARD_PREFIX) + PokerNowConstants.HOLE_CARD_PREFIX.length
            val indexOfHoleCardsEnd: Int =
                singleHandHistoryLine.indexOf(PokerNowConstants.DOUBLE_QUOTE, indexOfHoleCardsStart)
            yourHoleCards = singleHandHistoryLine.substring(indexOfHoleCardsStart, indexOfHoleCardsEnd)
            yourHoleCards = replaceColorsAndTens(yourHoleCards)
            yourHoleCards = ConversionConstants.OPEN_BRACKET + yourHoleCards + ConversionConstants.CLOSED_BRACKET
        }
        if (singleHandHistoryLine.contains(PokerNowConstants.HOLE_CARD_PREFIX)) {
            singleHandHistoryLine = bufferedReader.readLine()
        }
        var anteLines = StringUtils.EMPTY
        while (singleHandHistoryLine.contains(PokerNowConstants.ANTE_PREFIX)) {
            singleHandHistoryLine = singleHandHistoryLine!!.replace(
                ConversionConstants.AND_GO_ALL_IN + StringUtils.SPACE,
                StringUtils.EMPTY
            )
            var anteLine: String = singleHandHistoryLine.replace(
                (PokerNowConstants.ANTE_PREFIX + ConversionConstants.ONE_OR_MORE_DIGITS_GROUP_REGEX).toRegex(),
                ": posts the ante \\$" + ConversionConstants.FIRST_AND_SECOND_REGEX_GROUP_MATCH
            )
            anteLine = stripDateAndEntryOrderFromCsv(anteLine)
            anteLines += anteLine + System.lineSeparator()
            singleHandHistoryLine = bufferedReader.readLine()
        }
        val indexOfSmallBlindPoster: Int = singleHandHistoryLine.indexOf(PokerNowConstants.SMALL_BLIND_PREFIX)
        var smallBlindLine = StringUtils.EMPTY
        val smallBlindPlayerName: String
        if (indexOfSmallBlindPoster >= 0) {
            singleHandHistoryLine = singleHandHistoryLine!!.replace(
                ConversionConstants.AND_GO_ALL_IN + StringUtils.SPACE,
                StringUtils.EMPTY
            )
            smallBlindPlayerName = singleHandHistoryLine.substring(0, indexOfSmallBlindPoster)
            smallBlindLine = singleHandHistoryLine.replace(
                (PokerNowConstants.SMALL_BLIND_PREFIX + ConversionConstants.ONE_OR_MORE_DIGITS_GROUP_REGEX).toRegex(),
                ": posts small blind \\$" + ConversionConstants.FIRST_AND_SECOND_REGEX_GROUP_MATCH
            )
            smallBlindLine = stripDateAndEntryOrderFromCsv(smallBlindLine)
            lastRaiseAmountByPlayer[smallBlindPlayerName] = smallBlindAmount
        }
        singleHandHistoryLine = bufferedReader.readLine()
        singleHandHistoryLine =
            singleHandHistoryLine.replace(ConversionConstants.AND_GO_ALL_IN + StringUtils.SPACE, StringUtils.EMPTY)
        val bigBlindPlayerName =
            singleHandHistoryLine.substring(0, singleHandHistoryLine.indexOf(PokerNowConstants.BIG_BLIND_PREFIX))
        var bigBlindLine: String = singleHandHistoryLine.replace(
            (PokerNowConstants.BIG_BLIND_PREFIX + ConversionConstants.ONE_OR_MORE_DIGITS_GROUP_REGEX).toRegex(),
            ": posts big blind \\$" + ConversionConstants.FIRST_AND_SECOND_REGEX_GROUP_MATCH
        )
        bigBlindLine = stripDateAndEntryOrderFromCsv(bigBlindLine)
        lastRaiseAmountByPlayer[bigBlindPlayerName] = bigBlindAmount
        singleHandHistoryLine = bufferedReader.readLine()
        var missingSmallBlindsLines = StringUtils.EMPTY
        while (singleHandHistoryLine.contains(PokerNowConstants.SMALL_BLIND_PREFIX)) {
            singleHandHistoryLine = singleHandHistoryLine!!.replace(
                ConversionConstants.AND_GO_ALL_IN + StringUtils.SPACE,
                StringUtils.EMPTY
            )
            var missingSmallBlindLine: String = singleHandHistoryLine.replace(
                (PokerNowConstants.SMALL_BLIND_PREFIX + ConversionConstants.ONE_OR_MORE_DIGITS_GROUP_REGEX).toRegex(),
                ": posts the ante \\$" + ConversionConstants.FIRST_AND_SECOND_REGEX_GROUP_MATCH
            )
            missingSmallBlindLine = stripDateAndEntryOrderFromCsv(missingSmallBlindLine)
            missingSmallBlindsLines += missingSmallBlindLine + System.lineSeparator()
            singleHandHistoryLine = bufferedReader.readLine()
        }
        var missingBigBlindLine = StringUtils.EMPTY
        if (singleHandHistoryLine.contains(PokerNowConstants.BIG_BLIND_PREFIX)) {
            singleHandHistoryLine = singleHandHistoryLine!!.replace(
                ConversionConstants.AND_GO_ALL_IN + StringUtils.SPACE,
                StringUtils.EMPTY
            )
            val missingBigBlindName =
                singleHandHistoryLine.substring(0, singleHandHistoryLine.indexOf(PokerNowConstants.BIG_BLIND_PREFIX))
            missingBigBlindLine = singleHandHistoryLine.replace(
                (PokerNowConstants.BIG_BLIND_PREFIX + ConversionConstants.ONE_OR_MORE_DIGITS_GROUP_REGEX).toRegex(),
                ": posts big blind \\$" + ConversionConstants.FIRST_AND_SECOND_REGEX_GROUP_MATCH
            )
            missingBigBlindLine = stripDateAndEntryOrderFromCsv(missingBigBlindLine)
            lastRaiseAmountByPlayer[missingBigBlindName] = bigBlindAmount
            singleHandHistoryLine = bufferedReader.readLine()
        }
        val straddlePlayerName: String
        var straddleLine = StringUtils.EMPTY
        if (singleHandHistoryLine.contains(PokerNowConstants.STRADDLE_PREFIX)) {
            singleHandHistoryLine = singleHandHistoryLine!!.replace(
                ConversionConstants.AND_GO_ALL_IN + StringUtils.SPACE,
                StringUtils.EMPTY
            )
            straddlePlayerName =
                singleHandHistoryLine.substring(0, singleHandHistoryLine.indexOf(PokerNowConstants.STRADDLE_PREFIX))
            straddleLine = singleHandHistoryLine.replace(
                (" posts a straddle of " + ConversionConstants.ONE_OR_MORE_DIGITS_GROUP_REGEX).toRegex(),
                ": posts straddle \\$" + ConversionConstants.FIRST_AND_SECOND_REGEX_GROUP_MATCH
            )
            straddleLine = stripDateAndEntryOrderFromCsv(straddleLine)
            lastRaiseAmountByPlayer[straddlePlayerName] = straddleAmount
            latestBetTotalAmount = straddleAmount
            singleHandHistoryLine = bufferedReader.readLine()
        }
        if (anteLines.isNotEmpty()) {
            convertedSingleHandHistory += anteLines
        }
        if (smallBlindLine.isNotEmpty()) {
            convertedSingleHandHistory += smallBlindLine + System.lineSeparator()
        }
        convertedSingleHandHistory += bigBlindLine + System.lineSeparator()
        if (missingSmallBlindsLines.isNotEmpty()) {
            convertedSingleHandHistory += missingSmallBlindsLines
        }
        if (straddleLine.isNotEmpty()) {
            convertedSingleHandHistory += straddleLine + System.lineSeparator()
        }
        if (missingBigBlindLine.isNotEmpty()) {
            convertedSingleHandHistory += missingBigBlindLine + System.lineSeparator()
        }
        convertedSingleHandHistory += PokerStarsConstants.HOLE_CARDS
        if (isReadYourHoleCards && yourHoleCards.isNotEmpty()) {
            convertedSingleHandHistory += PokerStarsConstants.DEALT_TO_HERO + yourUniqueName +
                    StringUtils.SPACE + yourHoleCards + System.lineSeparator()
        }
        var endBoard = StringUtils.EMPTY
        do {
            if (singleHandHistoryLine.contains(PokerStarsConstants.SHOWS_ACTION)) {
                convertedSingleHandHistory = handleShowAction(singleHandHistoryLine, convertedSingleHandHistory)
                singleHandHistoryLine = bufferedReader.readLine()
            } else if (!singleHandHistoryLine.contains(PokerStarsConstants.COLLECTED)) {
                var gameAction: String
                if (singleHandHistoryLine.contains(PokerStarsConstants.BETS)) {
                    gameAction = handleBetAction(singleHandHistoryLine)
                } else if (singleHandHistoryLine.contains(PokerStarsConstants.CALLS)) {
                    gameAction = handleCallAction(singleHandHistoryLine)
                } else if (singleHandHistoryLine!!.contains(" check")) {
                    gameAction = singleHandHistoryLine.replace(
                        PokerStarsConstants.CHECKS,
                        ConversionConstants.DOUBLE_POINT.toString() + PokerStarsConstants.CHECKS
                    )
                } else if (singleHandHistoryLine.contains(PokerStarsConstants.FOLDS)) {
                    gameAction = singleHandHistoryLine.replace(
                        PokerStarsConstants.FOLDS,
                        ConversionConstants.DOUBLE_POINT.toString() + PokerStarsConstants.FOLDS
                    )
                } else if (singleHandHistoryLine.contains(PokerStarsConstants.RAISES_ACTION)) {
                    gameAction = handleRaiseAction(singleHandHistoryLine)
                } else if (singleHandHistoryLine.contains("Uncalled bet of ")) {
                    gameAction = singleHandHistoryLine.replaceFirst(
                        (PokerNowConstants.DOUBLE_QUOTE + "Uncalled bet of " + ConversionConstants.ONE_OR_MORE_DIGITS_GROUP_REGEX).toRegex(),
                        "Uncalled bet (\\$" + ConversionConstants.FIRST_AND_SECOND_REGEX_GROUP_MATCH + ")"
                    )
                } else {
                    gameAction = handleNextBoardCard(singleHandHistoryLine)
                }
                gameAction = stripDateAndEntryOrderFromCsv(gameAction)
                endBoard = convertBoardInfos(endBoard, gameAction)
                if (gameAction.isNotEmpty()) {
                    convertedSingleHandHistory += gameAction + System.lineSeparator()
                }
                singleHandHistoryLine = bufferedReader.readLine()
            }
        } while (null != singleHandHistoryLine && !singleHandHistoryLine.contains(PokerStarsConstants.COLLECTED))
        convertedSingleHandHistory += PokerStarsConstants.SHOWDOWN
        val winningAmounts: MutableList<String> = ArrayList()
        val winningPlayers: MutableList<String> = ArrayList()
        do {
            var showdownAction = singleHandHistoryLine!!.replace(" shows a", PokerStarsConstants.SHOWS_ACTION)
            showdownAction = replaceColorsAndTens(showdownAction)
            showdownAction = showdownAction.replace(
                (" PokerStarsConstants.COLLECTED " + ConversionConstants.ONE_OR_MORE_DIGITS_GROUP_REGEX).toRegex(),
                " PokerStarsConstants.COLLECTED \\$" + ConversionConstants.FIRST_AND_SECOND_REGEX_GROUP_MATCH
            )
            if (showdownAction.contains(PokerStarsConstants.COLLECTED)) {
                handleCollectedFromPotAction(winningAmounts, winningPlayers, showdownAction)
            } else if (showdownAction.contains(PokerStarsConstants.SHOWS_ACTION)) {
                showdownAction = handleShowCardsAction(showdownAction)
            }
            showdownAction = stripDateAndEntryOrderFromCsv(showdownAction)
            if (showdownAction.contains("(")) {
                showdownAction = showdownAction.substring(0, showdownAction.indexOf(" ("))
            }
            if (showdownAction.trim { it <= ' ' }.isNotEmpty()) {
                convertedSingleHandHistory += showdownAction + System.lineSeparator()
            }
            singleHandHistoryLine = bufferedReader.readLine()
        } while (null != singleHandHistoryLine && !singleHandHistoryLine.contains(PokerNowConstants.HAND_END))
        convertedSingleHandHistory += PokerStarsConstants.SUMMARY
        val gameSummary = createConvertedHandSummary(
            playerSummary, endBoard, winningAmounts,
            winningPlayers
        )
        convertedSingleHandHistory += gameSummary + System.lineSeparator() +
                System.lineSeparator() + System.lineSeparator() + System.lineSeparator()
        return convertedSingleHandHistory
    }

    fun readHandNumber(singleHandHistoryLine: String?): String {
        return singleHandHistoryLine!!.substring(
            singleHandHistoryLine.indexOf(ConversionConstants.HAND_NUMBER_PREFIX_CHAR) + 1,
            singleHandHistoryLine.indexOf(
                StringUtils.SPACE,
                singleHandHistoryLine.indexOf(ConversionConstants.HAND_NUMBER_PREFIX_CHAR)
            )
        )
    }

    private fun createConvertedHandSummary(
        playerSummary: String, endBoard: String, winningAmounts: List<String>,
        winningPlayers: List<String>
    ): String {
        var gameSummary =
            "Total pot $" + calculateTotalPotFromWinnings(winningAmounts) + " | Rake $0" + System.lineSeparator()
        if (endBoard.isNotEmpty()) {
            gameSummary += "Board " + endBoard + System.lineSeparator()
        }
        gameSummary += playerSummary.replace("(.+) \\(.+\\)".toRegex(), "$1 folded")
        for (n in winningPlayers.indices) {
            val winningPlayer = winningPlayers[n]
            gameSummary = gameSummary.replace(
                "$winningPlayer folded",
                winningPlayer + " PokerStarsConstants.COLLECTED (" + winningAmounts[n] + ")"
            )
            gameSummary = gameSummary.replace(" showed  and won ", PokerStarsConstants.COLLECTED)
        }
        gameSummary = replaceTens(gameSummary)
        return gameSummary
    }

    private fun calculateTotalPotFromWinnings(winningAmounts: List<String>): Double {
        var totalPot = 0.0
        for (winningAmount in winningAmounts) {
            totalPot += winningAmount.substring(1).toDouble()
        }
        return totalPot
    }

    private fun convertBoardInfos(endBoard: String, gameAction: String): String {
        var tempEndBoard = endBoard
        if (gameAction.contains(PokerStarsConstants.FLOP)) {
            tempEndBoard = gameAction.replace(PokerStarsConstants.FLOP + StringUtils.SPACE, StringUtils.EMPTY)
            tempEndBoard = tempEndBoard.replace(ConversionConstants.OPEN_BRACKET, StringUtils.EMPTY)
            tempEndBoard = tempEndBoard.replace(ConversionConstants.CLOSED_BRACKET, StringUtils.EMPTY)
            tempEndBoard = ConversionConstants.OPEN_BRACKET + tempEndBoard + ConversionConstants.CLOSED_BRACKET
            lastRaiseAmountByPlayer.clear()
            latestBetTotalAmount = 0.0
        }
        if (gameAction.contains(PokerStarsConstants.TURN)) {
            tempEndBoard = gameAction.replace(PokerStarsConstants.TURN + StringUtils.SPACE, StringUtils.EMPTY)
            tempEndBoard = tempEndBoard.replace(ConversionConstants.OPEN_BRACKET, StringUtils.EMPTY)
            tempEndBoard = tempEndBoard.replace(ConversionConstants.CLOSED_BRACKET, StringUtils.EMPTY)
            tempEndBoard = ConversionConstants.OPEN_BRACKET + tempEndBoard + ConversionConstants.CLOSED_BRACKET
            lastRaiseAmountByPlayer.clear()
            latestBetTotalAmount = 0.0
        }
        if (gameAction.contains(PokerStarsConstants.RIVER)) {
            tempEndBoard = gameAction.replace(PokerStarsConstants.RIVER + StringUtils.SPACE, StringUtils.EMPTY)
            tempEndBoard = tempEndBoard.replace(ConversionConstants.OPEN_BRACKET, StringUtils.EMPTY)
            tempEndBoard = tempEndBoard.replace(ConversionConstants.CLOSED_BRACKET, StringUtils.EMPTY)
            tempEndBoard = ConversionConstants.OPEN_BRACKET + tempEndBoard + ConversionConstants.CLOSED_BRACKET
            lastRaiseAmountByPlayer.clear()
            latestBetTotalAmount = 0.0
        }
        return tempEndBoard
    }

    private fun handleBetAction(singleHandHistoryLine: String?): String {
        val betAmount = singleHandHistoryLine!!.replace(
            (ConversionConstants.ONE_OR_MORE_CHARACTERS + PokerStarsConstants.BETS + ConversionConstants.ONE_OR_MORE_DIGITS_AND_CHARS_REGEX).toRegex(),
            ConversionConstants.FIRST_AND_SECOND_REGEX_GROUP_MATCH
        )
        val bettingPlayer = singleHandHistoryLine.substring(0, singleHandHistoryLine.indexOf(PokerStarsConstants.BETS))
        latestBetTotalAmount = betAmount.toDouble()
        lastRaiseAmountByPlayer[bettingPlayer] = latestBetTotalAmount
        var gameAction: String = singleHandHistoryLine.replace(
            PokerStarsConstants.BETS,
            ConversionConstants.DOUBLE_POINT.toString() + PokerStarsConstants.BETS + PokerStarsConstants.DOLLAR_CHAR
        )
        gameAction = gameAction.replace(ConversionConstants.AND_GO_ALL_IN, StringUtils.EMPTY)
        return gameAction
    }

    private fun handleShowAction(singleHandHistoryLine: String?, convertedSingleHandHistory: String?): String? {
        var tempConvertedSingleHandHistory = convertedSingleHandHistory
        var showdownAction: String =
            singleHandHistoryLine!!.replace(PokerStarsConstants.SHOWS_ACTION + "a ", PokerStarsConstants.SHOWS_ACTION)
        showdownAction =
            showdownAction.replace(ConversionConstants.COMMA_CHAR.toString() + StringUtils.SPACE, StringUtils.SPACE)
        showdownAction = showdownAction.replace(PokerNowConstants.SPADES, PokerStarsConstants.SPADES)
        showdownAction = showdownAction.replace(PokerNowConstants.HEARTS, PokerStarsConstants.HEARTS)
        showdownAction = showdownAction.replace(PokerNowConstants.CLUBS, PokerStarsConstants.CLUBS)
        showdownAction = showdownAction.replace(PokerNowConstants.DIAMONDS, PokerStarsConstants.DIAMONDS)
        showdownAction = handleShowCardsAction(showdownAction)
        showdownAction = stripDateAndEntryOrderFromCsv(showdownAction)
        if (showdownAction.trim { it <= ' ' }.isNotEmpty()) {
            tempConvertedSingleHandHistory += showdownAction + System.lineSeparator()
        }
        return tempConvertedSingleHandHistory
    }

    private fun handleShowCardsAction(showdownAction: String): String {
        var tempShowdownAction = showdownAction
        var showedHand: String = tempShowdownAction.substring(
            tempShowdownAction.indexOf(PokerStarsConstants.SHOWS_ACTION) + PokerStarsConstants.SHOWS_ACTION.length,
            tempShowdownAction.indexOf('.', tempShowdownAction.indexOf(PokerStarsConstants.SHOWS_ACTION))
        )
        showedHand = ConversionConstants.OPEN_BRACKET + showedHand + ConversionConstants.CLOSED_BRACKET
        tempShowdownAction = tempShowdownAction.substring(
            0,
            tempShowdownAction.indexOf(PokerStarsConstants.SHOWS_ACTION) + PokerStarsConstants.SHOWS_ACTION.length
        ) +
                showedHand + PokerNowConstants.DOUBLE_QUOTE
        tempShowdownAction = tempShowdownAction.replace(
            PokerStarsConstants.SHOWS_ACTION,
            ConversionConstants.DOUBLE_POINT.toString() + PokerStarsConstants.SHOWS_ACTION
        )
        tempShowdownAction = replaceTens(tempShowdownAction)
        return tempShowdownAction
    }

    private fun handleCollectedFromPotAction(
        winningAmounts: MutableList<String>,
        winningPlayers: MutableList<String>,
        showdownAction: String
    ) {
        val winningPlayer = showdownAction.substring(0, showdownAction.indexOf(PokerStarsConstants.COLLECTED))
        var indexOfWinningPlayer = winningPlayers.size
        if (winningPlayers.contains(winningPlayer)) {
            indexOfWinningPlayer = winningPlayers.indexOf(winningPlayer)
        } else {
            winningPlayers.add(winningPlayer)
        }
        val winningAmount = showdownAction.substring(
            showdownAction.indexOf(PokerStarsConstants.COLLECTED) + PokerStarsConstants.COLLECTED.length,
            showdownAction.indexOf(
                StringUtils.SPACE,
                showdownAction.indexOf(PokerStarsConstants.COLLECTED) + PokerStarsConstants.COLLECTED.length
            )
        )
        if (indexOfWinningPlayer == winningAmounts.size) {
            winningAmounts.add(winningAmount)
        } else {
            // Cumulate Wins from run it twice or hi/lo pots
            val existingWinningAmountString = winningAmounts[indexOfWinningPlayer].substring(1)
            val existingWinningAmount = existingWinningAmountString.toDouble()
            val newWinningAmountString = winningAmount.substring(1)
            val newWinningAmount = newWinningAmountString.toDouble()
            winningAmounts[indexOfWinningPlayer] =
                ConversionConstants.DOLLAR_SIGN + (existingWinningAmount + newWinningAmount).toString()
        }
    }

    private fun handleNextBoardCard(singleHandHistoryLine: String?): String {
        var gameAction: String = singleHandHistoryLine!!.replace(
            "\"[F|f]lop( \\(second run\\))?:".toRegex(),
            PokerStarsConstants.FLOP + ConversionConstants.FIRST_REGEX_GROUP_MATCH
        )
        gameAction = gameAction.replace(
            "\"[T|t]urn( \\(second run\\))?:".toRegex(),
            PokerStarsConstants.TURN + ConversionConstants.FIRST_REGEX_GROUP_MATCH
        )
        gameAction = gameAction.replace(
            "\"[R|r]iver( \\(second run\\))?:".toRegex(),
            PokerStarsConstants.RIVER + ConversionConstants.FIRST_REGEX_GROUP_MATCH
        )
        gameAction = replaceColorsAndTens(gameAction)
        if (gameAction.contains(PokerStarsConstants.FLOP) || gameAction.contains(PokerStarsConstants.TURN) || gameAction.contains(
                PokerStarsConstants.RIVER
            )
        ) {
            lastRaiseAmountByPlayer.clear()
        }
        return gameAction
    }

    private fun handleRaiseAction(singleHandHistoryLine: String?): String {
        val raiseTotal = singleHandHistoryLine!!.replace(
            (ConversionConstants.ONE_OR_MORE_CHARACTERS + PokerStarsConstants.RAISES_TO + ConversionConstants.ONE_OR_MORE_DIGITS_AND_CHARS_REGEX).toRegex(),
            ConversionConstants.FIRST_AND_SECOND_REGEX_GROUP_MATCH
        )
        val raisingPlayer =
            singleHandHistoryLine.substring(0, singleHandHistoryLine.indexOf(PokerStarsConstants.RAISES_TO))
        val raiseAmount = raiseTotal.toDouble() - latestBetTotalAmount
        val roundedRaiseAmount = roundToCents(raiseAmount)
        latestBetTotalAmount = raiseTotal.toDouble()
        if (lastRaiseAmountByPlayer.containsKey(raisingPlayer)) {
            lastRaiseAmountByPlayer.remove(raisingPlayer)
        }
        lastRaiseAmountByPlayer[raisingPlayer] = latestBetTotalAmount
        var gameAction: String = singleHandHistoryLine.replace(
            PokerStarsConstants.RAISES_ACTION,
            ConversionConstants.DOUBLE_POINT.toString() + PokerStarsConstants.RAISES_ACTION
        )
        val replacement: String =
            PokerStarsConstants.RAISES_ACTION + PokerStarsConstants.DOLLAR_CHAR + roundedRaiseAmount + " to $"
        gameAction = gameAction.replace(PokerStarsConstants.RAISES_TO, replacement)
        gameAction = gameAction.replace(ConversionConstants.AND_GO_ALL_IN, StringUtils.EMPTY)
        return gameAction
    }

    private fun handleCallAction(singleHandHistoryLine: String?): String {
        var gameAction: String?
        val callTotal = singleHandHistoryLine!!.replace(
            (ConversionConstants.ONE_OR_MORE_CHARACTERS + PokerStarsConstants.CALLS + ConversionConstants.ONE_OR_MORE_DIGITS_AND_CHARS_REGEX).toRegex(),
            ConversionConstants.FIRST_AND_SECOND_REGEX_GROUP_MATCH
        )
        val callingPlayer = singleHandHistoryLine.substring(0, singleHandHistoryLine.indexOf(PokerStarsConstants.CALLS))
        val callTotalAmount = callTotal.toDouble()
        gameAction = singleHandHistoryLine
        if (lastRaiseAmountByPlayer.containsKey(callingPlayer)) {
            val callPart = callTotalAmount - lastRaiseAmountByPlayer[callingPlayer]!!
            val roundedCallPart = roundToCents(callPart)
            gameAction = singleHandHistoryLine.replaceFirst(
                (PokerStarsConstants.CALLS + ConversionConstants.ONE_OR_MORE_DIGITS_REGEX).toRegex(),
                PokerStarsConstants.CALLS + roundedCallPart
            )
        }
        lastRaiseAmountByPlayer.remove(callingPlayer)
        lastRaiseAmountByPlayer[callingPlayer] = callTotalAmount
        gameAction = gameAction.replace(
            PokerStarsConstants.CALLS,
            ConversionConstants.DOUBLE_POINT.toString() + PokerStarsConstants.CALLS + PokerStarsConstants.DOLLAR_CHAR
        )
        gameAction = gameAction.replace(ConversionConstants.AND_GO_ALL_IN, StringUtils.EMPTY)
        return gameAction
    }

    private fun roundToCents(calculatedDouble: Double): Double {
        return (100 * calculatedDouble).roundToInt() / 100.0
    }

    companion object {
        private val handConversionLog = LogManager.getLogger(PokerNowSingleHandConverter::class.java)
    }
}
