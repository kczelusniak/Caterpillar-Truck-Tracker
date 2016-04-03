/*
 * Copyright 2016 Nathan Clark, Krzysztof Czelusniak, Michael Holwey, Dakota Leonard
 */

package edu.bradley.cattrucktracker;

import java.util.concurrent.ExecutionException;

import microsoft.aspnet.signalr.client.Platform;
import microsoft.aspnet.signalr.client.SignalRFuture;
import microsoft.aspnet.signalr.client.http.android.AndroidPlatformComponent;
import microsoft.aspnet.signalr.client.hubs.HubConnection;
import microsoft.aspnet.signalr.client.hubs.HubProxy;

class BackEnd {
    private final HubProxy hubProxy;
    private final String serialNumber = "0";

    BackEnd() throws ExecutionException, InterruptedException {
        AndroidPlatformComponent androidPlatformComponent = new AndroidPlatformComponent();
        Platform.loadPlatformComponent(androidPlatformComponent);

        HubConnection hubConnection
                = new HubConnection("http://bradley-capstone-app.azurewebsites.net");
        hubProxy = hubConnection.createHubProxy("SensorHub");

        SignalRFuture<Void> signalRFuture = hubConnection.start();
        signalRFuture.get();
    }

    void sendTruckLocationAndSpeed(double latitude, double longitude, Float speed) {
        hubProxy.invoke("PostGeo", serialNumber, latitude, longitude, speed);
    }

    void sendTruckStateAndTime(TruckState.Type truckState) {
        long currentTime = System.currentTimeMillis();
        hubProxy.invoke("PostStateChange", truckState, currentTime, serialNumber);
    }
}