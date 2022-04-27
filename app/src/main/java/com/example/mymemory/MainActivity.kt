package com.example.mymemory

import android.animation.ArgbEvaluator
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mymemory.models.BoardSize
import com.example.mymemory.models.MemoryGame
import com.example.mymemory.models.UserImageList
import com.example.mymemory.utils.EXTRA_BOARD_SIZE
import com.example.mymemory.utils.EXTRA_GAME_NAME
import com.github.jinatonic.confetti.CommonConfetti
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.squareup.picasso.Picasso


class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val CREATE_REQUEST_CODE = 248

    }

    private lateinit var rvBoard : RecyclerView
    private lateinit var tvNumMoves : TextView
    private lateinit var tvNumPairs : TextView
    private lateinit var  clRoot: CoordinatorLayout

    private val db = Firebase.firestore
    private var gameName: String? = null
    private var customGameImages: List<String>? = null
    private lateinit var adapter: MemoryBoardAdapter
    private var boardSize: BoardSize = BoardSize.EASY
    private lateinit var memoryGame: MemoryGame


    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        clRoot = findViewById(R.id.clRoot)
        rvBoard = findViewById(R.id.rvBoard)
        tvNumMoves = findViewById(R.id.tvNumMoves)
        tvNumPairs = findViewById(R.id.tvNumPairs)

      setUpBoard()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId){
            R.id.mi_refresh ->{
                if(memoryGame.getNumMoves() > 0 && !memoryGame.haweWonGame()){
                    showAlertDialog("Quit your current game?", null, View.OnClickListener {
                        setUpBoard()
                    })
                }else {
                    setUpBoard()
                }
            }
            R.id.mi_new_size ->{
                showNewSizeDialog()
                return true
            }
            R.id.mu_custom ->{
                showCreationDialog()
                return true
            }
            R.id.mi_download ->{
                showDownloadDialog()
            }
        }
        return super.onOptionsItemSelected(item)
    }



    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == CREATE_REQUEST_CODE && resultCode == Activity.RESULT_OK){
            val customGameName = data?.getStringExtra(EXTRA_GAME_NAME)
            if (customGameName == null){
                Log.e(TAG, "Got null custom game name form CreateActivity")
                return
            }
            downloadGame(customGameName)
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun showDownloadDialog() {
        val boardDownloadView  =  LayoutInflater.from(this).inflate(R.layout.dialog_download_board, null)
        showAlertDialog("Fetch memory game", boardDownloadView, View.OnClickListener {
            //text of game name that user wants to download
            val etDownloadGame  = boardDownloadView.findViewById<EditText>(R.id.etDownloadGame)
            val gameToDownload = etDownloadGame.text.toString().trim()
            downloadGame(gameToDownload)

        })
    }
    private fun downloadGame(customGameName: String) {
        db.collection("games").document(customGameName).get().addOnSuccessListener { document ->
            val userImageList = document.toObject(UserImageList::class.java)
            if (userImageList?.images == null){
                Log.e(TAG, "invalid custom game data from Firestore")
                Snackbar.make(clRoot, "Sorry we coutdn't find any such game '$customGameName'", Snackbar.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }
            val numCards = userImageList.images.size * 2
            boardSize = BoardSize.getByValue(numCards)
            customGameImages = userImageList.images
            for (imageUrl in userImageList.images){
                if (!imageUrl.isNullOrEmpty()){
                    Picasso.get().load(imageUrl).fetch()
                }
            }
            Snackbar.make(clRoot, "you're playing $customGameName", Snackbar.LENGTH_SHORT).show()
            gameName = customGameName
            setUpBoard()

        }.addOnFailureListener{ exception ->
            Log.e(TAG, "Exception when retrieving game", exception)
        }
    }

    private fun showCreationDialog() {
        val boardSizeView = LayoutInflater.from(this).inflate(R.layout.dialog_board_size, null)
        val radioGroupSize = boardSizeView.findViewById<RadioGroup>(R.id.radioGroup)

        showAlertDialog("Create your own memory board" , boardSizeView, View.OnClickListener {
            val desiredBoardSize = when (radioGroupSize.checkedRadioButtonId){
                R.id.rbEasy -> BoardSize.EASY
                R.id.rbMed -> BoardSize.MEDIUM
                else -> BoardSize.HARD
            }
            val intent =  Intent(this,CreateAcitvity::class.java)
            intent.putExtra(EXTRA_BOARD_SIZE, desiredBoardSize)
            startActivityForResult(intent, CREATE_REQUEST_CODE)
        })
    }

    private fun showNewSizeDialog() {
        val boardSizeView = LayoutInflater.from(this).inflate(R.layout.dialog_board_size, null)
        val radioGroupSize = boardSizeView.findViewById<RadioGroup>(R.id.radioGroup)
        when(boardSize){
            BoardSize.EASY -> radioGroupSize.check(R.id.rbEasy)
            BoardSize.MEDIUM -> radioGroupSize.check(R.id.rbMed)
            BoardSize.HARD -> radioGroupSize.check(R.id.rbHard)
        }
        showAlertDialog("Choose new size" , boardSizeView, View.OnClickListener {
            boardSize = when (radioGroupSize.checkedRadioButtonId){
                R.id.rbEasy -> BoardSize.EASY
                R.id.rbMed -> BoardSize.MEDIUM
                else -> BoardSize.HARD
            }
            gameName = null
            customGameImages = null
            setUpBoard()
        })
    }

    private fun showAlertDialog(title: String,view: View? , positiveClickListener: View.OnClickListener) {
        AlertDialog.Builder(this)

            .setTitle(title)
            .setView(view)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Ok",){_ , _ ->
                positiveClickListener.onClick(null)
            }.show()
    }

    private fun setUpBoard() {
        supportActionBar?.title = gameName ?: "test" //getString(R.string.app_name)
        when(boardSize){
            BoardSize.EASY -> {
                tvNumPairs.text = "Easy: 4 x 2"
                tvNumMoves.text = "Pairs: 0/4"
            }
            BoardSize.MEDIUM -> {
                tvNumPairs.text = "Medium: 6 x 3"
                tvNumMoves.text = "Pairs: 0/9"
            }
            BoardSize.HARD -> {
                tvNumPairs.text = "Hard: 6 x 4"
                tvNumMoves.text = "Pairs: 0/12"

            }
        }
        memoryGame = MemoryGame(boardSize, customGameImages )
        tvNumPairs.setTextColor(ContextCompat.getColor(this,R.color.color_progress_nome))
        adapter = MemoryBoardAdapter(this, boardSize, memoryGame.cards, object: MemoryBoardAdapter.CardClickListener{
            override fun onCardClicked(position: Int) {
                updateGameWithFlip(position)
            }
        })
        rvBoard.adapter = adapter
        rvBoard.setHasFixedSize(true)
        rvBoard.layoutManager = GridLayoutManager(this, boardSize.getWidth())

    }
    private fun updateGameWithFlip(position: Int) {
        if(memoryGame.haweWonGame()){
            Snackbar.make(clRoot, "You already won!", Snackbar.LENGTH_LONG).show()
            return
        }
        if(memoryGame.isCardFaceUp(position)){
            Snackbar.make(clRoot, "Invalid move!", Snackbar.LENGTH_SHORT).show()

            return
        }
        if (memoryGame.flipCard(position)){
            val color = ArgbEvaluator().evaluate(
                memoryGame.numPairsFound.toFloat() / boardSize.getNumPairs(),
                ContextCompat.getColor(this,R.color.color_progress_nome),
                ContextCompat.getColor(this,R.color.color_progress_full)

            ) as Int
            tvNumPairs.setTextColor(color)
            tvNumPairs.text = "Pairs: ${memoryGame.numPairsFound} / ${boardSize.getNumPairs()}"

            if(memoryGame.haweWonGame()){
                Snackbar.make(clRoot,"You won! Congratulations.", Snackbar.LENGTH_LONG).show()
                CommonConfetti.rainingConfetti(clRoot,  intArrayOf(Color.YELLOW,Color.GREEN, Color.MAGENTA)).oneShot()
            }
        }
        tvNumMoves.text = " Moves: ${memoryGame.getNumMoves()}"
        adapter.notifyDataSetChanged()
    }
}