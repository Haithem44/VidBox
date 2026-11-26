<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="?attr/selectableItemBackground"
    android:gravity="center_vertical"
    android:minHeight="64dp"
    android:orientation="horizontal"
    android:paddingStart="16dp"
    android:paddingTop="12dp"
    android:paddingEnd="16dp"
    android:paddingBottom="12dp">

    <ImageView
        android:layout_width="28dp"
        android:layout_height="28dp"
        android:layout_marginEnd="16dp"
        android:src="@android:drawable/ic_media_play"
        android:contentDescription="@null" />

    <TextView
        android:id="@+id/itemTitle"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:ellipsize="end"
        android:maxLines="2"
        android:textAppearance="?attr/textAppearanceBodyLarge" />

</LinearLayout>
