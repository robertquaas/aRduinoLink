package com.arbotics.databot.sciencejournal;

import android.content.Context;
import android.widget.Button;

public class DatabotSelectButton extends Button {
    public final String name;
    public final String UUID;
    public String rssi;
    public final Integer id;

    public DatabotSelectButton(Context context, String name, String UUID, String rssi, Integer id){
        super(context);
        this.name = name;
        this.UUID = UUID;
        this.rssi = rssi;
        this.id = id;

        this.setText(name + " Rssi:" + rssi);
        this.setBackgroundColor(getResources().getColor(R.color.databotDarkBlue));
        this.setTextColor(getResources().getColor(R.color.databotWhite));
        this.setId(id);

    }

    public void setActive(){
        this.setBackgroundColor(getResources().getColor(R.color.databotGreen));
    }

    public void unSetActive(){
        this.setBackgroundColor(getResources().getColor(R.color.databotDarkBlue));
    }

    public void updateText(){
        this.setText(name + " Rssi:" + rssi);
    }
}
