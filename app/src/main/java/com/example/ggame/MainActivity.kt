package com.example.ggame

import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import java.util.LinkedList
import java.util.Queue
import kotlin.random.Random

class MainActivity : ComponentActivity() {

    private lateinit var maze: Array<Array<Int>>
    private val keys = mutableSetOf<Pair<Int, Int>>()
    private val lockedDoors = mutableSetOf<Pair<Int, Int>>()
    private var playerX = 0
    private var playerY = 0
    private var previousX = 0
    private var previousY = 0
    private var hasKey = false
    private var startTime: Long = 0
    private var bestTime: Long = Long.MAX_VALUE
    private lateinit var mazeTextView: TextView
    private lateinit var buttonUp: Button
    private lateinit var buttonDown: Button
    private lateinit var buttonLeft: Button
    private lateinit var buttonRight: Button
    private lateinit var buttonRegenerate: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadBestTime()
        createUI()
        showMainMenu()
    }

    private fun loadBestTime() {
        val sharedPref = getPreferences(Context.MODE_PRIVATE)
        bestTime = sharedPref.getLong("BEST_TIME", Long.MAX_VALUE)
    }

    private fun saveBestTime(time: Long) {
        val sharedPref = getPreferences(Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putLong("BEST_TIME", time)
            apply()
        }
    }

    private fun showMainMenu() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Main Menu")

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        val widthInput = EditText(this).apply {
            hint = "Maze Width"
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(2))
        }

        val heightInput = EditText(this).apply {
            hint = "Maze Height"
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(2))
        }

        layout.addView(widthInput)
        layout.addView(heightInput)

        builder.setView(layout)
        builder.setPositiveButton("Start Game") { dialog, _ ->
            val newWidth = widthInput.text.toString().toIntOrNull() ?: 10
            val newHeight = heightInput.text.toString().toIntOrNull() ?: 10
            val width = if (newWidth in 5..10) newWidth else 10
            val height = if (newHeight in 5..10) newHeight else 10

            initializeGame(width, height)
            dialog.dismiss()
        }
        builder.setNegativeButton("Exit") { _, _ ->
            finish()
        }
        builder.setNeutralButton("Results") { _, _ ->
            showResults()
        }
        builder.setCancelable(false)
        builder.create().show()
    }

    private fun showResults() {
        val builder = AlertDialog.Builder(this)
        val bestTimeStr = if (bestTime == Long.MAX_VALUE) "No record" else "${bestTime / 1000} seconds"
        builder.setTitle("Results")
            .setMessage("Your best time: $bestTimeStr")
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun initializeGame(width: Int, height: Int) {
        resetGameState()
        generateMaze(width, height)
        while (!isSolvable(0, 0, width - 1, height - 1)) {
            generateMaze(width, height)
        }
        playerX = 0
        playerY = 0
        previousX = 0
        previousY = 0
        hasKey = false
        startTime = System.currentTimeMillis()
        buttonRegenerate.visibility = Button.GONE
        updateMazeView(width, height)
    }

    private fun resetGameState() {
        mazeTextView.text = ""
        keys.clear()
        lockedDoors.clear()
    }

    private fun createUI() {
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        mazeTextView = TextView(this).apply {
            textSize = 20f
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
            setPadding(16, 16, 16, 16)
            setBackgroundColor(0xFFDDDDDD.toInt())
            setTextColor(0xFF000000.toInt())
        }

        buttonUp = Button(this).apply { text = "Up" }
        buttonDown = Button(this).apply { text = "Down" }
        buttonLeft = Button(this).apply { text = "Left" }
        buttonRight = Button(this).apply { text = "Right" }
        buttonRegenerate = Button(this).apply {
            text = "Regenerate Maze"
            visibility = Button.GONE
        }

        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        val horizontalLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        horizontalLayout.addView(buttonLeft)
        horizontalLayout.addView(buttonRight)

        buttonLayout.addView(buttonUp)
        buttonLayout.addView(horizontalLayout)
        buttonLayout.addView(buttonDown)
        buttonLayout.addView(buttonRegenerate)

        mainLayout.addView(mazeTextView)
        mainLayout.addView(buttonLayout)

        setContentView(mainLayout)

        buttonUp.setOnClickListener { move(4) }
        buttonDown.setOnClickListener { move(8) }
        buttonLeft.setOnClickListener { move(1) }
        buttonRight.setOnClickListener { move(2) }
        buttonRegenerate.setOnClickListener {
            initializeGame(maze[0].size, maze.size)
            updateMazeView(maze[0].size, maze.size)
            buttonRegenerate.visibility = Button.GONE
        }
    }

    private fun generateMaze(width: Int, height: Int) {
        maze = Array(height) { Array(width) { 0 } }
        val directions = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
        val stack = mutableListOf<Pair<Int, Int>>()
        val visited = Array(height) { Array(width) { false } }

        fun isInside(x: Int, y: Int) = x in 0 until width && y in 0 until height

        stack.add(0 to 0)
        visited[0][0] = true

        while (stack.isNotEmpty()) {
            val (cx, cy) = stack.last()
            val unvisitedNeighbors = directions.map { (dx, dy) -> cx + dx to cy + dy }
                .filter { (nx, ny) -> isInside(nx, ny) && !visited[ny][nx] }

            if (unvisitedNeighbors.isEmpty()) {
                stack.removeAt(stack.lastIndex)
            } else {
                val (nx, ny) = unvisitedNeighbors.random()
                when {
                    nx > cx -> {
                        maze[cy][cx] = maze[cy][cx] or 2
                        maze[ny][nx] = maze[ny][nx] or 1
                    }
                    nx < cx -> {
                        maze[cy][cx] = maze[cy][cx] or 1
                        maze[ny][nx] = maze[ny][nx] or 2
                    }
                    ny > cy -> {
                        maze[cy][cx] = maze[cy][cx] or 8
                        maze[ny][nx] = maze[ny][nx] or 4
                    }
                    ny < cy -> {
                        maze[cy][cx] = maze[cy][cx] or 4
                        maze[ny][nx] = maze[ny][nx] or 8
                    }
                }
                visited[ny][nx] = true
                stack.add(nx to ny)
            }
        }
        placeKeyAndDoor(width, height)
    }

    private fun placeKeyAndDoor(width: Int, height: Int) {
        keys.clear()
        lockedDoors.clear()

        val keyPosition = generateRandomPosition(width, height, listOf(0 to 0, width - 1 to height - 1))
        keys.add(keyPosition)

        val doorPosition = generateRandomPosition(width, height, listOf(0 to 0, width - 1 to height - 1, keyPosition))
        lockedDoors.add(doorPosition)

        maze[height - 1][width - 1] = 0
    }

    private fun generateRandomPosition(width: Int, height: Int, exclude: List<Pair<Int, Int>>): Pair<Int, Int> {
        var position: Pair<Int, Int>
        do {
            position = Pair(Random.nextInt(width), Random.nextInt(height))
        } while (exclude.contains(position))
        return position
    }

    private fun isSolvable(startX: Int, startY: Int, endX: Int, endY: Int): Boolean {
        val directions = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
        val queue: Queue<Pair<Int, Int>> = LinkedList()
        val visited = Array(endY + 1) { Array(endX + 1) { false } }

        queue.add(startX to startY)
        visited[startY][startX] = true

        while (queue.isNotEmpty()) {
            val (x, y) = queue.remove()
            if (x == endX && y == endY) return true

            for ((dx, dy) in directions) {
                val nx = x + dx
                val ny = y + dy
                if (nx in 0..endX && ny in 0..endY && !visited[ny][nx] && canMove(x, y, getDirection(dx, dy))) {
                    queue.add(nx to ny)
                    visited[ny][nx] = true
                }
            }
        }
        return false
    }

    private fun getDirection(dx: Int, dy: Int): Int {
        return when {
            dx == 1 -> 2
            dx == -1 -> 1
            dy == 1 -> 8
            dy == -1 -> 4
            else -> 0
        }
    }

    private fun canMove(x: Int, y: Int, direction: Int): Boolean {
        if (x !in maze[0].indices || y !in maze.indices) return false
        val cell = maze[y][x]
        val newX = x + if (direction == 1) -1 else if (direction == 2) 1 else 0
        val newY = y + if (direction == 4) -1 else if (direction == 8) 1 else 0
        return (cell and direction) != 0 || (lockedDoors.contains(newX to newY))
    }


    private fun move(direction: Int) {
        previousX = playerX
        previousY = playerY
        when (direction) {
            1 -> if (canMove(playerX, playerY, 1)) playerX--
            2 -> if (canMove(playerX, playerY, 2)) playerX++
            4 -> if (canMove(playerX, playerY, 4)) playerY--
            8 -> if (canMove(playerX, playerY, 8)) playerY++
        }
        handlePlayerMovement()
    }

    private fun handlePlayerMovement() {
        if (playerX == maze[0].size - 1 && playerY == maze.size - 1 && lockedDoors.isEmpty()) {
            onGameWon()
        } else if (playerX == maze[0].size - 1 && playerY == maze.size - 1 && lockedDoors.isNotEmpty()) {
            showToast("You need to open all doors to win!")
            playerX = previousX
            playerY = previousY
        } else if (playerX != previousX || playerY != previousY) {
            checkKey()
            checkLockedDoor()
            updateMazeView(maze[0].size, maze.size)
        }
    }

    private fun onGameWon() {
        val endTime = System.currentTimeMillis()
        val elapsedTime = endTime - startTime
        if (elapsedTime < bestTime) {
            bestTime = elapsedTime
            saveBestTime(elapsedTime)
            showToast("Congratulations! New record time: ${elapsedTime / 1000} seconds")
        } else {
            showToast("Congratulations! You completed the maze in ${elapsedTime / 1000} seconds")
        }
        mazeTextView.postDelayed({
            showMainMenu()
        }, 3000)
    }

    private fun checkKey() {
        if (keys.contains(playerX to playerY)) {
            keys.remove(playerX to playerY)
            hasKey = true
            showToast("You found a key!")
        }
    }

    private fun checkLockedDoor() {
        if (lockedDoors.contains(playerX to playerY)) {
            if (hasKey) {
                lockedDoors.remove(playerX to playerY)
                hasKey = false
                showToast("You used a key to open the door!")
                maze[playerY][playerX] = 15
            } else {
                showToast("You need a key to open this door!")
            }
        }
    }

    private fun updateMazeView(width: Int, height: Int) {
        val sb = StringBuilder()
        val cellWidth = 3

        for (y in 0 until height) {
            for (x in 0 until width) {
                val cellContent = when {
                    x == playerX && y == playerY -> "P"
                    keys.contains(x to y) -> "K"
                    lockedDoors.contains(x to y) -> "D"
                    else -> maze[y][x].toString()
                }
                sb.append(cellContent.padEnd(cellWidth, ' '))
            }
            sb.append("\n")
            if (y < height - 1) {
                sb.append(" ".repeat(cellWidth * width))
                sb.append("\n")
            }
        }

        mazeTextView.text = sb.toString()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}