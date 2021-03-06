package transponders.transmob;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.AsyncTask.Status;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnTouchListener;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The Fragment for displaying the Go card balance and history.
 * Must be opened from GocardLoginFragment after a successful login only. 
 * 
 *
 */
public class GocardDisplayFragment extends Fragment {
	
	private DefaultHttpClient httpClient;
	
	private String balanceResult;
	private TableLayout balanceTable, historyTable;
	private TextView balanceLabel, balancenumLabel, asofLabel;
	private FragmentActivity activity;
	private HttpThread httpThread;
	
	private CountDownLatch lock;
	
	@Override
	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		
		activity = getActivity();
		
		if (getArguments() == null) {
			balanceResult ="";

		} else {
			if (balanceResult == null) {
				balanceResult = getArguments().getString("BALANCE_RESULT");
				httpClient = GocardLoginFragment.getHttpClient();
			}
		}
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,Bundle savedInstanceState) 
	{
		View view = inflater.inflate(R.layout.gocard_page, container, false);
		
		balanceTable = (TableLayout) view.findViewById(R.id.balance_table);
		historyTable = (TableLayout) view.findViewById(R.id.history_table);
		
		balanceLabel = (TextView) view.findViewById(R.id.balance_label);
		balancenumLabel = (TextView) view.findViewById(R.id.balancenum_label);
		asofLabel = (TextView) view.findViewById(R.id.asof_label);
		
		int density = getResources().getDisplayMetrics().densityDpi;
        if(density <= DisplayMetrics.DENSITY_HIGH)
        {
        	balanceLabel.setTextSize(24);
        	balancenumLabel.setTextSize(24);
        	balanceTable.setPadding(20, 0, 20, 0);
        }
		
		balanceTable.setOnTouchListener(new OnTouchListener()
		{
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				return true;
			}	
		});
		
		activity.setProgressBarIndeterminateVisibility(true);
		parseResultAsBalance(balanceResult);
		
		return view;
	}
	
	public String postData(String url) 
	{	    
	    HttpPost httppost = new HttpPost(url);
	    
	    Log.d("postData()", "inside postData()");
	    String result = "not yet...";

	    try 
	    {
	        // Execute HTTP Post Request
	        HttpResponse response = httpClient.execute(httppost);
	        
	        if (response != null) {
                result = EntityUtils.toString(response.getEntity());
            }
	        
	    } catch (ClientProtocolException e) {
	        result = "ClientProtocolException";
	    } catch (IOException e) {
	        result = "IOException";
	    }
	    
	    return result;
	} 
	
	private class HttpThread extends AsyncTask<String, Void, String>
	{
		@Override
		protected String doInBackground(String... params) 
		{
			String res = postData(params[0]);
			return res;
		}
		
		@Override
	    protected void onPostExecute(String result) {
	    	Log.d("JSONRequest", "postexecute");
	    	
	    	super.onPostExecute(result);
	    	
	    	parseResult(result);
	    	if (lock != null) {
	    		lock.countDown();
	    	}
	    }	
	}
	
	/**
	 * parse the result given by HttpClient. Calls other functions based on the determined format
	 * @param result HTML page for either history or balance pages
	 */
	public void parseResult(String result) {
		Log.d("GoCard", result);
		Log.d("GoCard", "resultEndOfFile=" + result.substring(result.length()-20));
		
		if (result.contains("<table id=\"travel-history\"")) 
		{	
			parseResultAsHistory(result);
			activity.setProgressBarIndeterminateVisibility(false);	
		} 
		else 
		{	
        	Toast.makeText(activity.getApplicationContext(), "Something went wrong.", Toast.LENGTH_SHORT).show();
		}
	}
	
	/**
	 * Takes the results of the balance table and adds them to the TableLayout.
	 * Proceeds to execute another HttpThread for the history table.
	 * @param result a HTML page. It must be in the expected format.
	 */
	public void parseResultAsBalance(String result) {
		int indexOfCardBalance = result.indexOf("<table id=\"balance-table\"");
		int indexOfEndOfCardBalance = result.indexOf("</table>", indexOfCardBalance);
		String tableSubstr = result.substring(indexOfCardBalance, indexOfEndOfCardBalance);
		String[] tableRowStrings = tableSubstr.split("<tr>"); 
			
		Log.d("tableRowStrings i", 2 + "");
		String rowStr = tableRowStrings[2];
    	int endOfFirstColumn = rowStr.indexOf("</td>");
    	String dateText = rowStr.substring(rowStr.indexOf("<td>")+4, endOfFirstColumn);
    	asofLabel.setText("As of " + dateText.trim());
  
        String costText = rowStr.substring(rowStr.indexOf("<td>", endOfFirstColumn)+4, rowStr.indexOf("</td>", endOfFirstColumn+4));
    	balancenumLabel.setText(costText);
        Log.d("GoCard", "dateText="+dateText);
        Log.d("GoCard", "costText="+costText);
        
		httpThread = new HttpThread();
		String url = "https://gocard.translink.com.au/webtix/tickets-and-fares/go-card/online/history";
		httpThread.execute(url);
	}
	
	/**
	 * Takes the results of the history table and adds them to the TableLayout.
	 * @param result a HTML page. It must be in the expected format.
	 */
	public void parseResultAsHistory(String result) 
	{	
		int indexOfHistory = result.indexOf("<table id=\"travel-history\"");
		int indexOfEndOfHistory = result.indexOf("</table>", indexOfHistory);
		String tableSubstr = result.substring(indexOfHistory, indexOfEndOfHistory);
		String[] tableRowStrings = tableSubstr.split("<tr class=\"sub-heading\">"); 
		
		for (int i = 1; i < tableRowStrings.length; i++) 
		{
			String rowStr = tableRowStrings[i];
	        Context tableContext = historyTable.getContext();
			TableRow newRow = new TableRow(tableContext);
			Context rowContext = newRow.getContext();
			
			DisplayMetrics scale = activity.getResources().getDisplayMetrics();
        	int height = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 30, scale);
        	newRow.setMinimumHeight(height);
        	TextView transactionDate = new TextView(rowContext);
        	int endOfFirstColumn = rowStr.indexOf("</td>");
        	String dateText = rowStr.substring(rowStr.indexOf("<td colspan")+16, endOfFirstColumn).trim();
        	transactionDate.setText(dateText);
        	transactionDate.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        	 
        	TableRow.LayoutParams param0 = new TableRow.LayoutParams();
            param0.column = 0;
            param0.span = 85;
            param0.weight = 1;
            
            transactionDate.setLayoutParams(param0);
            transactionDate.setPadding(15, 2, 0, 2);
            transactionDate.setBackgroundResource(R.color.ferry_blue);
            transactionDate.setTextColor(Color.WHITE);
        	newRow.addView(transactionDate);
        	historyTable.addView(newRow);
        	
        	String[] rowsInDate = rowStr.split("<tr>");
        	
        	Log.d("goCard table", "rowsInDate " + rowsInDate.length);
        	for (int j=1; j<rowsInDate.length; j++) {
        		
        		Log.d("goCard table", "j " + j);
        		TableRow newRow2 = new TableRow(tableContext);
        		Context rowContext2 = newRow2.getContext();
        		
            	newRow2.setPadding(5, 7, 5, 9);
        		
        		String[] rowInDateCols = rowsInDate[j].split("<td");
        		
        		//debug
        		for (int k=0;k<rowInDateCols.length; k++) {
        			if (k!=0) {
        				Log.d("GoCard", "parsed rowInDateCols["+k+"]= "+ rowInDateCols[k].
        						substring(rowInDateCols[k].indexOf(">")+1, rowInDateCols[k].
        		        				indexOf("</td>")).trim());
        			}
        		}
        		
        		String timeText = rowInDateCols[1].substring(rowInDateCols[1].indexOf(">")+1, rowInDateCols[1].
        				indexOf("</td>")).trim();
        		String time2Text = rowInDateCols[3].substring(rowInDateCols[3].indexOf(">")+1, rowInDateCols[3].
        				indexOf("</td>")).trim();
        		
        		String touchOnText = rowInDateCols[2].substring(rowInDateCols[2].indexOf(">")+1, rowInDateCols[2].
        				indexOf("</td>")).trim();
        		touchOnText = touchOnText.replace("&#039;", "");
        		String touchOffText = rowInDateCols[4].substring(rowInDateCols[4].indexOf(">")+1, rowInDateCols[4].
        				indexOf("</td>")).trim();
        		touchOffText = touchOffText.replace("&#039;", "");
        		
        		String priceText = rowInDateCols[5].substring(rowInDateCols[5].indexOf(">")+1, rowInDateCols[5].
        				indexOf("</td>")).trim();
        		
        		if(priceText.equalsIgnoreCase("$0.00"))
        			priceText = "$ 0.00";
        		
        		if(priceText.contains("&nbsp;"))
        		{
        			priceText = touchOffText;
        			touchOffText = "Haven't touched off yet.";
        		}
        		
        		////////////////////////////////////////////////////////////////////////
        		
	            LinearLayout cell1 = new LinearLayout(rowContext);
	            cell1.setOrientation(LinearLayout.VERTICAL);
	            cell1.setMinimumHeight(height);
	            cell1.setGravity(Gravity.CENTER_VERTICAL);
	 
	            TableRow.LayoutParams param1 = new TableRow.LayoutParams();
	            param1.column = 0;
	            param1.span = 16;
	            param1.weight = 1;
	            cell1.setLayoutParams(param1);
	            
	            TextView onTime = new TextView(rowContext2);
	            onTime.setPadding(10, 0, -10, 0);            	
	      
	            TextView offTime = new TextView(rowContext2);
	            offTime.setPadding(10, 0, -10, 0);
	            
	            onTime.setText(timeText);
	            offTime.setText(time2Text);
	            
	            cell1.addView(onTime);
	            cell1.addView(offTime);
	            
	            newRow2.addView(cell1);
	            
	            /////////////////////////////////////////////////////////////////////////
	            
	            LinearLayout cell2 = new LinearLayout(rowContext);
	            cell2.setOrientation(LinearLayout.VERTICAL);
	            cell2.setMinimumHeight(height);
	            cell2.setGravity(Gravity.CENTER_VERTICAL);
	 
	            TableRow.LayoutParams param2 = new TableRow.LayoutParams();
	            param2.column = 1;
	            param2.span = 57;
	            param2.weight = 1;
	            cell2.setLayoutParams(param2);
	            
	            TextView touchOnLabel = new TextView(rowContext);
	            TextView touchOffLabel = new TextView(rowContext);
	            touchOnLabel.setPadding(0, 0, 0, 0);
	            touchOffLabel.setPadding(2, 0, 0, 0);
	            
	            touchOnLabel.setText(touchOnText);
	            touchOffLabel.setText(touchOffText);
	            
	            cell2.addView(touchOnLabel);
	            cell2.addView(touchOffLabel);
	            
	            newRow2.addView(cell2);
	            
	            //////////////////////////////////////////////////////////////////////////
	            
	            LinearLayout cell3 = new LinearLayout(rowContext);
	            cell3.setOrientation(LinearLayout.VERTICAL);
	            cell3.setMinimumHeight(height);
	            cell3.setGravity(Gravity.CENTER_VERTICAL);

	            TableRow.LayoutParams param3 = new TableRow.LayoutParams();
	            param3.column = 2;
	            param3.span = 10;
	            param3.weight = 1;
	            cell3.setLayoutParams(param3);
	            
	            TextView fareLabel = new TextView(rowContext2);
	            fareLabel.setText(priceText);
	            fareLabel.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
	            fareLabel.setTextColor(activity.getResources().getColor(R.color.train_orange));
	            
	            int density = getResources().getDisplayMetrics().densityDpi;
	            if(density <= DisplayMetrics.DENSITY_HIGH)
	            {
	            	param3.span = 12;
	            	fareLabel.setPadding(0, 0, 1, 0);
	            	cell3.setLayoutParams(param3);
	            }
	            
	            cell3.addView(fareLabel);
	            newRow2.addView(cell3);
	            
	            ///////////////////////////////////////////////////////////////////////////
	            
	            View separatorLine = new View(tableContext);
	            separatorLine.setBackgroundColor(getResources().getColor(R.color.separator_line));
	            separatorLine.setPadding(0, 0, 0, 0);
	            TableLayout.LayoutParams lineParam = new TableLayout.LayoutParams();
	            lineParam.height = 2;
	            separatorLine.setLayoutParams(lineParam);
	            
	            historyTable.addView(newRow2);
	            historyTable.addView(separatorLine);
        	}
		}
	}
	
	@Override
	public void onStop() 
	{
		activity.setProgressBarIndeterminateVisibility(false);
		
		// Prevents crash when user hits back before the asynctask is finished
	    if (this.httpThread != null && this.httpThread.getStatus() == Status.RUNNING) 
	    	this.httpThread.cancel(true);
	    
	    super.onStop();
	}
	
	/*testing functions*/
	public void setCountDownLatch(CountDownLatch lock) {
		this.lock = lock;
	
	}
	public TableLayout getBalanceTable() {
		return balanceTable;
	}
	public TableLayout getHistoryTable() {
		return historyTable;
	}
	/*end of testing functions */
}
