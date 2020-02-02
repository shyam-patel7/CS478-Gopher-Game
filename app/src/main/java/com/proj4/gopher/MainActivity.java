/* CS478 Project 4: Threads
 * Name:   Shyam Patel
 * NetID:  spate54
 * Date:   Nov 24, 2019
 */

package com.proj4.gopher;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.text.HtmlCompat;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;

public class MainActivity extends AppCompatActivity {
  // variable(s)
  private static final int   GUESS_WORKER_THREAD_A = 0;
  private static final int   GUESS_WORKER_THREAD_B = 1;
  private View               gameView              = null;
  private boolean            gameRunning           = false;
  private GridLayout         gameGrid              = null;
  private TextView           gameLog               = null;
  private boolean            gameLogGravity        = false;
  private Switch             continuousSw          = null;
  private boolean            continuousMode        = false;
  private Button             startButton           = null;
  private ImageView[][]      holes                 = new ImageView[10][10];
  private ImageView          gopher                = null;
  private ArrayList<Integer> guesses               = new ArrayList<>();
  private Random             random                = new Random();
  private Handler            handlerUI             = new Handler(Looper.getMainLooper());
  private WorkerThread       workerThreadA         = null;
  private WorkerThread       workerThreadB         = null;
  private Integer[]          gopherPos             = {-1,-1};
  private Integer[][]        nearMissPos           = {{-1,-1},{0,-1},{1,-1},{-1,0},
                                                      {1,0},{-1,1},{0,1},{1,1}};
  private Integer[][]        closePos              = {{-2,-2},{-1,-2},{0,-2},{1,-2},
                                                      {2,-2},{-2,-1},{2,-1},{-2,0},
                                                      {2,0},{-2,1},{2,1},{-2,2},
                                                      {-1,2},{0,2},{1,2},{2,2}};

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    gameView = findViewById(android.R.id.content);
    gameView.setAlpha(0f);

    // populate image view references for 10x10 grid
    for (int i = 0; i < holes.length; i++)
      for (int j = 0; j < holes[i].length; j++)
        holes[i][j] = findViewById(getResources()
          .getIdentifier("activity_main_iv_" + i + j, "id", getPackageName()));

    // link layout component(s)
    gameGrid     = findViewById(R.id.activity_main_gl);
    gameLog      = findViewById(R.id.activity_main_tv_log);
    continuousSw = findViewById(R.id.activity_main_sw_continuous);
    startButton  = findViewById(R.id.activity_main_btn_start);

    // set on checked change listener to continuous play mode switch
    continuousSw.setOnCheckedChangeListener((v, c) -> {
      if (c) { continuousMode = true;  logAppend(getString(R.string.continuous_on));  }
      else   { continuousMode = false; logAppend(getString(R.string.continuous_off)); }
    });

    // set on click listener to start button
    startButton.setOnClickListener(this::startGame);

    // create and start worker threads
    workerThreadA = new WorkerThread(getString(R.string.thread_a), GUESS_WORKER_THREAD_A);
    workerThreadB = new WorkerThread(getString(R.string.thread_b), GUESS_WORKER_THREAD_B);
    workerThreadA.start();
    workerThreadB.start();

    // animate opening transition
    new Handler().postDelayed(() -> gameView.animate().alpha(1f).setDuration(600), 600);
  }//end onCreate

  @Override protected void onDestroy() {
    super.onDestroy();

    if (workerThreadA != null) {
      // remove any pending posts/messages on worker thread A's handler
      workerThreadA.handler.removeCallbacksAndMessages(null);
      // quit worker thread A's looper
      if (workerThreadA.isAlive()) workerThreadA.quit();
    }
    if (workerThreadB != null) {
      // remove any pending posts/messages on worker thread B's handler
      workerThreadB.handler.removeCallbacksAndMessages(null);
      // quit worker thread B's looper
      if (workerThreadB.isAlive()) workerThreadB.quit();
    }
  }//end onDestroy

  // helper method to start new game
  private void startGame(@NonNull View v) {
    // disable and hide start button and continuous play mode switch
    v.setEnabled(false);
    v.animate().alpha(0.25f).setDuration(600);
    continuousSw.setEnabled(false);
    continuousSw.animate().alpha(0.25f).setDuration(600);

    // generate random gopher position and reference gopher image view
    gopherPos[0] = random.nextInt(10);
    gopherPos[1] = random.nextInt(10);
    gopher       = holes[gopherPos[0]][gopherPos[1]];

    // pre-compute near miss guess positions
    for (int i = 0; i < nearMissPos.length; i++)
      for (int j = 0; j < nearMissPos[i].length; j++)
        nearMissPos[i][j] = (j == 0) ?
          nearMissPos[i][j] + gopherPos[0] : nearMissPos[i][j] + gopherPos[1];

    // pre-compute close guess positions
    for (int i = 0; i < closePos.length;    i++)
      for (int j = 0; j < closePos[i].length;    j++)
        closePos[i][j]    = (j == 0) ?
          closePos[i][j]    + gopherPos[0] : closePos[i][j]    + gopherPos[1];

    // set start text in game log
    if (continuousMode) gameLog.setText(R.string.start_continuous_on);
    else                gameLog.setText(R.string.start_continuous_off);

    // animate holes to full opacity (2 possible styles)
    int r = random.nextInt(2);
    for (int i = 0; i < holes.length; i++)
      for (int j = 0; j < holes[i].length; j++) {
        ImageView hole = holes[i][j];
        new Handler().postDelayed(() -> { switch (r) {
          case 0: hole.animate().rotationX(360f).alpha(1f).setDuration(750); break;
          case 1: hole.animate().rotationY(360f).alpha(1f).setDuration(750); break;
        } }, (i + j) * 75);
      }

    // display gopher
    new Handler().postDelayed(this::addGopher, 2150);

    // send message for worker thread(s) to start making guess(es)
    Message msg;
    if (continuousMode) {
      msg = workerThreadA.handler.obtainMessage(GUESS_WORKER_THREAD_A);
      workerThreadA.handler.sendMessageDelayed(msg, 2750);
      msg = workerThreadB.handler.obtainMessage(GUESS_WORKER_THREAD_B);
      workerThreadB.handler.sendMessageDelayed(msg, 2750);
    } else {
      msg = workerThreadA.handler.obtainMessage(GUESS_WORKER_THREAD_A);
      workerThreadA.handler.sendMessageDelayed(msg, 2750);
    }
  }//end startGame

  // helper method to display gopher addition to grid
  private void addGopher() {
    // set game status to running
    gameRunning = true;

    // convert start button into restart button
    startButton.setText(R.string.restart);
    startButton.setEnabled(true);
    startButton.setOnClickListener(this::restartGame);
    startButton.animate().alpha(1f).setDuration(600);

    // show gopher at gopher position
    if (continuousMode) {
      hideGopher();
    } else {
      showGopher();
      new Handler().postDelayed(this::showHole,   150);
      new Handler().postDelayed(this::showGopher, 300);
      new Handler().postDelayed(this::hideGopher, 600);
    }
    logAppend(getString(R.string.gopher_hide) + getPosition(gopherPos));
  }//end addGopher

  // helper method to display guess of worker thread
  private synchronized void addGuess(int worker, Integer[] pos, int result) {
    // return if game is no longer running
    if (!gameRunning) return;

    // add guess to collection of guesses
    guesses.add(Integer.parseInt(pos[0] + "" + pos[1]));

    // reference appropriate game log string resource
    int gameLogRes;
    switch (result) {
      case WorkerThread.NEAR_MISS:     gameLogRes = (worker == GUESS_WORKER_THREAD_A) ?
             R.string.thread_a_near_miss     : R.string.thread_b_near_miss;     break;
      case WorkerThread.CLOSE:         gameLogRes = (worker == GUESS_WORKER_THREAD_A) ?
             R.string.thread_a_close         : R.string.thread_b_close;         break;
      case WorkerThread.COMPLETE_MISS: gameLogRes = (worker == GUESS_WORKER_THREAD_A) ?
             R.string.thread_a_complete_miss : R.string.thread_b_complete_miss; break;
      default:                         gameLogRes = -1;
    }

    // animate guess of worker thread on grid, display log text and schedule worker's next guess
    switch (worker) {
      case GUESS_WORKER_THREAD_A:
        showHoleA(pos);
        if (!continuousMode) {
          new Handler().postDelayed(() -> showHole (pos), 150);
          new Handler().postDelayed(() -> showHoleA(pos), 300);
        }
        logAppend(getString(gameLogRes) + getPosition(pos), R.color.colorDarkThreadA);
        workerThreadA.handler.post(() -> {
          // shuffle worker thread A's collection of next guesses (1 in 4 chance)
          if (!workerThreadA.nextGuesses.isEmpty() && random.nextInt(4) == 0)
            Collections.shuffle(workerThreadA.nextGuesses);
          workerThreadA.scheduleNextGuess();
        }); break;
      case GUESS_WORKER_THREAD_B:
        showHoleB(pos);
        if (!continuousMode) {
          new Handler().postDelayed(() -> showHole (pos), 150);
          new Handler().postDelayed(() -> showHoleB(pos), 300);
        }
        logAppend(getString(gameLogRes) + getPosition(pos), R.color.colorDarkThreadB);
        workerThreadB.scheduleNextGuess(); break;
    }
  }//end addGuess

  // helper method to display successful guess of worker thread
  private synchronized void success(int worker) {
    // return if game is no longer running
    if (!gameRunning) return;

    // set game status to not running and disable restart button
    gameRunning = false;
    startButton.setEnabled(false);
    startButton.animate().alpha(0.25f).setDuration(2000);

    // animate successful guess of worker thread on grid and display log text
    switch (worker) {
      case GUESS_WORKER_THREAD_A:
        showGopherA();
        if (!continuousMode) {
          new Handler().postDelayed(this::hideGopher,  150);
          new Handler().postDelayed(this::showGopherA, 300);
        }
        logAppend(getString(R.string.thread_a_success) +
            getPosition(gopherPos), R.color.colorDarkThreadA); break;
      case GUESS_WORKER_THREAD_B:
        showGopherB();
        if (!continuousMode) {
          new Handler().postDelayed(this::hideGopher,  150);
          new Handler().postDelayed(this::showGopherB, 300);
        }
        logAppend(getString(R.string.thread_b_success) +
            getPosition(gopherPos), R.color.colorDarkThreadB); break;
    }

    // animate gopher to center of grid, display success text and hide holes
    new Handler().postDelayed(() -> {
      gopher.animate()
        .x(gameGrid.getWidth() / 2 - 56)
        .y(gameGrid.getHeight() / 2 + 96)
        .scaleX(4f).scaleY(4f).alpha(0f)
        .setDuration(3000);

      TextView success = (findViewById(R.id.activity_main_tv_success));
      success.setVisibility(View.VISIBLE);
      success.animate()
        .y(gameGrid.getHeight() / 3)
        .scaleX(2f).scaleY(2f).alpha(1f)
        .setDuration(1500)
        .setListener(new AnimatorListenerAdapter() {
          @Override public void onAnimationEnd(Animator a) {
            super.onAnimationEnd(a);
            success.animate().alpha(0f).setDuration(1200);
          }//end onAnimationEnd
        });

      for (int i = 0; i < holes.length; i++)
        for (int j = 0; j < holes[i].length; j++) {
          ImageView hole = holes[i][j];
          if (!hole.equals(gopher))
            new Handler().postDelayed(() ->
              hole.animate().alpha(0f).setDuration(1000), (i + j) * 100);
        }
    }, 2400);

    // restart game
    new Handler().postDelayed(this::restartGame, 5200);
  }//end success

  // helper methods to show, hide and remove gopher at gopher position
  private void showGopher()  { gopher.setImageResource(R.drawable.ic_gopher);      }
  private void hideGopher()  { gopher.setImageResource(R.drawable.ic_gopher_hide); }
  private void showGopherA() { gopher.setImageResource(R.drawable.ic_gopher_a);    }
  private void showGopherB() { gopher.setImageResource(R.drawable.ic_gopher_b);    }
  private void showHole()    { gopher.setImageResource(R.drawable.ic_hole);        }

  // helper methods to show hole at position p
  private void showHole (@NonNull Integer[] p)
    { holes[p[0]][p[1]].setImageResource(R.drawable.ic_hole);   }
  private void showHoleA(@NonNull Integer[] p)
    { holes[p[0]][p[1]].setImageResource(R.drawable.ic_hole_a); }
  private void showHoleB(@NonNull Integer[] p)
    { holes[p[0]][p[1]].setImageResource(R.drawable.ic_hole_b); }

  // helper methods to return row and column of position in printable format
  private String getPosition(@NonNull Integer[] p) {
    return " row " + (p[0] + 1) + ", col " + (p[1] + 1);
  }//end getPosition

  // helper method to append text to game log
  private void logAppend(String text) {
    logCheckGravity();
    gameLog.append(text + ".\n");
  }//end logAppend

  // helper method to append text with thread color to game log
  private void logAppend(String text, int c) {
    logCheckGravity();
    gameLog.append(HtmlCompat.fromHtml(
        "<font color=" + getColor(c) + ">" + text + ".</font>",
        HtmlCompat.FROM_HTML_MODE_LEGACY));
    gameLog.append("\n");
  }//end logAppend

  // helper method to set game log text gravity to bottom when it displays 16 lines of text
  private void logCheckGravity() {
    if (!gameLogGravity && gameLog.getLineCount() > 15) {
      gameLogGravity = true;
      gameLog.setGravity(Gravity.BOTTOM);
      gameLog.setMovementMethod(new ScrollingMovementMethod());
    }
  }//end logSetGravity

  // helper method to restart game
  private void restartGame() {
    // animate closing transition
    gameView.animate().alpha(0f).setDuration(600);
    new Handler().postDelayed(this::recreate, 900);
  }//end restartGame

  // helper method to restart game via button
  private void restartGame(@NonNull View v) {
    // set game status to not running
    gameRunning = false;
    // disable restart button
    v.setEnabled(false);
    // animate closing transition
    gameView.animate().alpha(0f).setDuration(600);
    new Handler().postDelayed(this::recreate, 900);
  }//end restartGame

  // class that encapsulates worker thread
  private class WorkerThread extends HandlerThread {
    // thread variable(s)
    private static final int     SUCCESS         = 2;
    private static final int     DISASTER        = 3;
    private static final int     NEAR_MISS       = 4;
    private static final int     CLOSE           = 5;
    private static final int     COMPLETE_MISS   = 6;
    private int                  worker             ;
    private Handler              handler         = null;
    private ArrayList<Integer[]> nextGuesses     = new ArrayList<>();
    private Integer[]            currGuess       = {0,0};
    private final Integer[][]    nearMissGuesses = {{-1,-1},{0,-1},{1,-1},{-1,0},
                                                    {1,0},{-1,1},{0,1},{1,1}};
    private final Integer[][]    closeGuesses    = {{-2,-2},{-1,-2},{0,-2},{1,-2},
                                                    {2,-2},{-2,-1},{2,-1},{-2,0},
                                                    {2,0},{-2,1},{2,1},{-2,2},
                                                    {-1,2},{0,2},{1,2},{2,2}};

    // class constructor
    private WorkerThread(String name, int w) { super(name); worker = w; }

    // attach handler to looper when it is ready
    @Override protected void onLooperPrepared() {
      handler = new Handler(getLooper()) {
        @Override public void handleMessage(Message msg) {
          // return if game is no longer running
          if (!gameRunning) return;

          switch (msg.what) {
            // worker thread A's guess strategy :
            case GUESS_WORKER_THREAD_A:
              // make random guess if have no educated guesses, otherwise use next educated guess
              if (nextGuesses.isEmpty()) {
                currGuess[0] = random.nextInt(10);
                currGuess[1] = random.nextInt(10);
              } else {
                while (currGuess.length > 0) {
                  currGuess = nextGuesses.get(0);
                  nextGuesses.remove(0);
                  if (currGuess[0] > -1 && currGuess[1] > -1 &&
                      currGuess[0] < 10 && currGuess[1] < 10) break;
                }
              }

              // post guess to UI thread handler and, if necessary, populate next educated guesses
              switch (postToHandlerUI()) {
                case NEAR_MISS: for (Integer[] p : nearMissGuesses)
                  nextGuesses.add(new Integer[]{currGuess[0] + p[0], currGuess[1] + p[1]}); break;
                case CLOSE:     for (Integer[] p : closeGuesses)
                  nextGuesses.add(new Integer[]{currGuess[0] + p[0], currGuess[1] + p[1]}); break;
              } break;

            // worker thread B's guess strategy :
            case GUESS_WORKER_THREAD_B:
              // always make random guess
              currGuess[0] = random.nextInt(10);
              currGuess[1] = random.nextInt(10);

              // post guess to handler UI
              postToHandlerUI(); break;
          }
        }//end handleMessage
      };//end handler
    }//end onLooperPrepared

    // helper method to post runnable with guess actions to UI thread
    private int postToHandlerUI() {
      // return if game is no longer running
      if (!gameRunning) return -1;

      // check current guess position and post corresponding runnable to UI thread
      if (currGuess[0].equals(gopherPos[0]) && currGuess[1].equals(gopherPos[1])) {
        // (1) successful guess
        handlerUI.postDelayed(() -> success(worker),                            200);
        return SUCCESS;
      } else if (guesses.contains(Integer.parseInt(currGuess[0] + "" + currGuess[1]))) {
        // (2) disaster guess
        handlerUI.postDelayed(() -> {
          // return if game is no longer running
          if (!gameRunning) return;

          // display log text and schedule next guess
          switch (worker) {
            case GUESS_WORKER_THREAD_A: logAppend(getString(R.string.thread_a_disaster) +
                                          getPosition(currGuess), R.color.colorDarkThreadA);
                                        scheduleNextGuess(); break;
            case GUESS_WORKER_THREAD_B: logAppend(getString(R.string.thread_b_disaster) +
                                          getPosition(currGuess), R.color.colorDarkThreadB);
                                        scheduleNextGuess(); break;
          } },                                                                  200);
        return DISASTER;
      } else if (Arrays.stream(nearMissPos).anyMatch(p ->
          currGuess[0].equals(p[0]) && currGuess[1].equals(p[1]))) {
        // (3) near miss guess
        handlerUI.postDelayed(() -> addGuess(worker, currGuess, NEAR_MISS),     200);
        return NEAR_MISS;
      } else if (Arrays.stream(closePos).anyMatch(p ->
          currGuess[0].equals(p[0]) && currGuess[1].equals(p[1]))) {
        // (4) close guess
        handlerUI.postDelayed(() -> addGuess(worker, currGuess, CLOSE),         200);
        return CLOSE;
      } else {
        // (5) complete miss guess
        handlerUI.postDelayed(() -> addGuess(worker, currGuess, COMPLETE_MISS), 200);
        return COMPLETE_MISS;
      }
    }//end postToHandlerUI

    // helper method to schedule next guess for worker thread based on game mode
    private void scheduleNextGuess() {
      // return if game is no longer running
      if (!gameRunning) return;

      Message msg;
      switch (worker) {
        case GUESS_WORKER_THREAD_A:
          msg = (continuousMode) ?          handler.obtainMessage(GUESS_WORKER_THREAD_A) :
                              workerThreadB.handler.obtainMessage(GUESS_WORKER_THREAD_B);
          if (continuousMode)               handler.sendMessageDelayed(msg, 600);
          else                workerThreadB.handler.sendMessageDelayed(msg, 550); break;
        case GUESS_WORKER_THREAD_B:
          msg = (continuousMode) ?          handler.obtainMessage(GUESS_WORKER_THREAD_B) :
                              workerThreadA.handler.obtainMessage(GUESS_WORKER_THREAD_A);
          if (continuousMode)               handler.sendMessageDelayed(msg, 600);
          else                workerThreadA.handler.sendMessageDelayed(msg, 550); break;
      }
    }//end scheduleNextGuess
  }//end class WorkerThread
}//end class MainActivity
