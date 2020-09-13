#!/bin/bash

TGT=../app/src/main/res
# clone of https://github.com/google/material-design-icons.git
SRC=~/git/material-design-icons/png

VAR=materialicons/48dp/1x

function image() {
  echo $SRC/$1/$2/$VAR/baseline_$2_black_48dp.png
}

convert $(image av play_arrow) -scale 32x32 $TGT/drawable/play_black.png
convert $(image action home) -negate -scale 32x32 $TGT/drawable/home_white.png
convert $(image av play_arrow) -negate -scale 32x32 $TGT/drawable/play_white.png
convert $(image navigation menu) -negate -scale 32x32 $TGT/drawable/menu_white.png
convert $(image av pause) -scale 16x16 $TGT/drawable/music_small.png
convert $(image av volume_down) -scale 32x32 $TGT/drawable/minus_black.png
convert $(image av pause) -scale 32x32 $TGT/drawable/pause_black.png
convert $(image av volume_down) -negate -scale 32x32 $TGT/drawable/minus_white.png
convert $(image navigation arrow_downward) -negate -scale 32x32 $TGT/drawable/page_down_white.png
convert $(image action home) -scale 32x32 $TGT/drawable/home_black.png
convert $(image av volume_up) -negate -scale 32x32 $TGT/drawable/plus_white.png
convert $(image av stop) -negate -scale 32x32 $TGT/drawable/stop_white.png
convert $(image av volume_off) -negate -scale 32x32 $TGT/drawable/volume_white.png
convert $(image action search) -scale 32x32 $TGT/drawable/search_black.png
convert $(image hardware keyboard_voice) -negate -scale 16x16 $TGT/drawable/listen_small.png
convert $(image av skip_previous) -negate -scale 32x32 $TGT/drawable/prev_white.png
convert $(image file folder) -scale 16x16 $TGT/drawable/folder_small.png
convert $(image av volume_mute) -negate -scale 32x32 $TGT/drawable/volume_mute_white.png
convert $(image av skip_next) -scale 32x32 $TGT/drawable/next_black.png
convert $(image action power_settings_new) -scale 32x32 $TGT/drawable/power_black.png
convert $(image av volume_up) -scale 32x32 $TGT/drawable/plus_black.png
convert $(image navigation arrow_upward) -scale 32x32 $TGT/drawable/page_up_black.png
convert $(image action label) -negate -scale 16x16 $TGT/drawable/cursor_small.png
convert $(image av stop) -scale 32x32 $TGT/drawable/stop_black.png
convert $(image action power_settings_new) -negate -scale 32x32 $TGT/drawable/power_white.png
convert $(image navigation arrow_upward) -negate -scale 32x32 $TGT/drawable/page_up_white.png
convert $(image navigation menu) -scale 32x32 $TGT/drawable/menu_black.png
convert $(image av skip_next) -negate -scale 32x32 $TGT/drawable/next_white.png
convert $(image action search) -negate -scale 32x32 $TGT/drawable/search_white.png
convert $(image av skip_previous) -negate -scale 32x32 $TGT/drawable/prev_black.png
convert $(image av volume_mute) -scale 32x32 $TGT/drawable/volume_mute_black.png
convert $(image av pause) -negate -scale 32x32 $TGT/drawable/pause_white.png
convert $(image navigation arrow_back) -scale 32x32 $TGT/drawable/arrow_left_black.png
convert $(image navigation arrow_downward) -scale 32x32 $TGT/drawable/page_down_black.png
convert $(image av volume_mute) -scale 32x32 $TGT/drawable/volume_black.png
convert $(image navigation arrow_back) -negate -scale 32x32 $TGT/drawable/arrow_left_white.png
#convert $SRC/ -scale 64x64 $TGT/drawable-xhdpi/ic_action_like_red.png
#convert $SRC/ -scale 64x64 $TGT/drawable-xhdpi/ic_action_star_10.png
#convert $SRC/ -scale 64x64 $TGT/drawable-xhdpi/ic_action_star_0.png
#convert $SRC/ -scale 48x48 $TGT/drawable-hdpi/ic_action_like_red.png
#convert $SRC/ -scale 48x48 $TGT/drawable-hdpi/ic_action_star_10.png
#convert $SRC/ -scale 48x48 $TGT/drawable-hdpi/ic_action_star_0.png
#convert $SRC/ -scale 32x32 $TGT/drawable-mdpi/ic_action_like_red.png
#convert $SRC/ -scale 32x32 $TGT/drawable-mdpi/ic_action_star_10.png
#convert $SRC/ -scale 32x32 $TGT/drawable-mdpi/ic_action_star_0.png
#convert $SRC/ ../src/app/