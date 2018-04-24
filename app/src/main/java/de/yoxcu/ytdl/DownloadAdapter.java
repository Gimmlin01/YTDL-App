package de.yoxcu.ytdl;

import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.List;

/**
 * Created by yoxcu on 22.03.18.
 */

public class DownloadAdapter extends RecyclerView.Adapter<DownloadAdapter.DownloadViewHolder> {
    private CustomItemClickListener mListener;
    private List<Download> downloadsList;

    public DownloadAdapter(List<Download> downloadsList,CustomItemClickListener mListener) {
        this.downloadsList = downloadsList;
        this.mListener=mListener;
    }

    @Override
    public DownloadViewHolder onCreateViewHolder(final ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.download_list_row, parent, false);
        final DownloadViewHolder vh = new DownloadViewHolder(itemView);
        itemView.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                mListener.onItemClick(v,vh.getPosition());
            }
        });
        return vh;
    }

    @Override
    public void onBindViewHolder(DownloadViewHolder holder, int position) {
        Download download = downloadsList.get(position);
        String filename=download.getFilename();
        String videoUrl=download.getUrl();
        if (filename != null){
            holder.name.setText(filename);
        }else if (videoUrl != null){
            holder.name.setText(download.getUrl());
            Log.d("Downl","no file");
        }else{
            holder.name.setText("Fehler");
        }
    }


    @Override
    public int getItemCount() {
        return downloadsList.size();
    }


    public class DownloadViewHolder extends RecyclerView.ViewHolder{
        public TextView name;


        public DownloadViewHolder(View view) {
            super(view);
            name = (TextView) view.findViewById(R.id.nameView);
        }


    }

    public interface CustomItemClickListener {
        public void onItemClick(View v, int position);
    }
}
