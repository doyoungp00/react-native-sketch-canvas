
import React, { useState } from "react";
import PropTypes from "prop-types";
import { View, TouchableOpacity, Button, StyleSheet, SafeAreaView ,Alert } from "react-native";
import SketchCanvas from "./src/SketchCanvas";
import { requestPermissions } from "./src/handlePermissions";
import { ViewPropTypes } from "deprecated-react-native-prop-types";
import ColorPicker from 'react-native-wheel-color-picker';
import { RgbaColorPicker } from "react-colorful";
import Slider from "@react-native-community/slider"


export default class RNSketchCanvas extends React.Component {


    static propTypes = {
        containerStyle: ViewPropTypes.style,
        canvasStyle: ViewPropTypes.style,
        onStrokeStart: PropTypes.func,
        onStrokeChanged: PropTypes.func,
        onStrokeEnd: PropTypes.func,
        onClosePressed: PropTypes.func,
        onUndoPressed: PropTypes.func,
        onClearPressed: PropTypes.func,
        onPathsChange: PropTypes.func,
        user: PropTypes.string,

        closeComponent: PropTypes.node,
        eraseComponent: PropTypes.node,
        undoComponent: PropTypes.node,
        clearComponent: PropTypes.node,
        saveComponent: PropTypes.node,

        //////////////////////////////////////////////////////////////////
        strokeColors: PropTypes.arrayOf(PropTypes.shape({ color: PropTypes.string })),
        strokeComponent: PropTypes.func,
        strokeSelectedComponent: PropTypes.func,


        //////////////////////////////////////////////////////////////////

        strokeWidthComponent: PropTypes.func,
        defaultStrokeIndex: PropTypes.number,
        defaultStrokeWidth: PropTypes.number,


        minStrokeWidth: PropTypes.number,
        maxStrokeWidth: PropTypes.number,
        strokeWidthStep: PropTypes.number,




        savePreference: PropTypes.func,
        onSketchSaved: PropTypes.func,

        localSourceImage: PropTypes.shape({
            backgroundImage: PropTypes.string,
            foregroundImage: PropTypes.string,
            directory: PropTypes.string,
            mode: PropTypes.string,
            maskname: PropTypes.string
        }),

        permissionDialogTitle: PropTypes.string,
        permissionDialogMessage: PropTypes.string
    };

    static defaultProps = {
        containerStyle: null,
        canvasStyle: null,
        onStrokeStart: () => { },
        onStrokeChanged: () => { },
        onStrokeEnd: () => { },
        onClosePressed: () => { },
        onUndoPressed: () => { },
        onClearPressed: () => { },
        onPathsChange: () => { },
        user: null,

        closeComponent: null,
        eraseComponent: null,
        undoComponent: null,
        clearComponent: null,
        saveComponent: null,


        //////////////////////////////////////////////////////////////////

        strokeComponent: null,
        strokeSelectedComponent: null,


        //////////////////////////////////////////////////////////////////
        strokeWidthComponent: null,



        //////////////////////////////////////////////////////////////////
        strokeColors: [
            { color: "#000000" },
            { color: "#FF0000" },
            { color: "#00FFFF" },
            { color: "#0000FF" },
            { color: "#0000A0" },
            { color: "#ADD8E6" },
            { color: "#800080" },
            { color: "#FFFF00" },
            { color: "#00FF00" },
            { color: "#FF00FF" },
            { color: "#FFFFFF" },
            { color: "#C0C0C0" },
            { color: "#808080" },
            { color: "#FFA500" },
            { color: "#A52A2A" },
            { color: "#800000" },
            { color: "#008000" },
            { color: "#808000" }
        ],

        //////////////////////////////////////////////////////////////////


        alphlaValues: ["33", "77", "AA", "FF"],


        //////////////////////////////////////////////////////////////////


        defaultStrokeIndex: 0,


        //////////////////////////////////////////////////////////////////
        defaultStrokeWidth: 3,

        minStrokeWidth: 3,
        maxStrokeWidth: 60,
        strokeWidthStep: 0.1,




        savePreference: null,
        onSketchSaved: () => { },

        localSourceImage: null,

        permissionDialogTitle: "",
        permissionDialogMessage: ""
    };

    constructor(props) {
        super(props);

        this.state = {
            color: props.strokeColors[props.defaultStrokeIndex].color,
            pick_color: '',
            strokeWidth: props.defaultStrokeWidth,
            alpha: "FF",
            isVisible: false,
            test_value: 255,
            before_erase_color: "",
            erase_toggle: true
        };

        this._colorChanged = false;
        this._strokeWidthStep = props.strokeWidthStep;
        this._alphaStep = -1;
    }

    clear() {
        this._sketchCanvas.clear();
    }

    undo() {
        return this._sketchCanvas.undo();
    }

    addPath(data) {
        this._sketchCanvas.addPath(data);
    }

    deletePath(id) {
        this._sketchCanvas.deletePath(id);
    }

    async save() {
        const isStoragePermissionAuthorized = await requestPermissions(
            this.props.permissionDialogTitle,
            this.props.permissionDialogMessage,
        );

        if (this.props.savePreference) {
            const p = this.props.savePreference();
            this._sketchCanvas.save(
                p.imageType,
                p.folder ? p.folder : '',
                p.filename,
                p.transparent,
                p.includeImage !== false,   // result is 'true' if variable is null
                p.cropToImageSize || false, // result is 'false' if variable is null
                p.cropToBackgroundSize || false,
                p.cropToForegroundSize || false
            );
        } else {
            const date = new Date();
            this._sketchCanvas.save(
                'png', // imageType
                'RNSketchCanvas', // folder
                date.getFullYear() +
                '-' +
                (date.getMonth() + 1) +
                '-' +
                ('0' + date.getDate()).slice(-2) +
                ' ' +
                ('0' + date.getHours()).slice(-2) +
                '-' +
                ('0' + date.getMinutes()).slice(-2) +
                '-' +
                ('0' + date.getSeconds()).slice(-2), // filename
                true, // transparent
                false, // includeImage
                false, // cropToImageSize
                
            );
        }
        Alert.alert(title = "Saved", message = `Folder : ${this.props.savePreference().folder} \nFileName : ${this.props.savePreference().filename}`);
        

    }

    nextStrokeWidth() {
        if (
            (this.state.strokeWidth >= this.props.maxStrokeWidth && this._strokeWidthStep > 0) ||
            (this.state.strokeWidth <= this.props.minStrokeWidth && this._strokeWidthStep < 0)
        )
            this._strokeWidthStep = -this._strokeWidthStep;
        this.setState({ strokeWidth: this.state.strokeWidth + this._strokeWidthStep });
    }

    _renderItem = ({ item, index }) => (
        <TouchableOpacity
            style={{ marginHorizontal: 2.5 }}
            onPress={() => {
                if (this.state.color === item.color) {
                    const index = this.props.alphlaValues.indexOf(this.state.alpha);
                    if (this._alphaStep < 0) {
                        this._alphaStep = index === 0 ? 1 : -1;
                        this.setState({ alpha: this.props.alphlaValues[index + this._alphaStep] });
                    } else {
                        this._alphaStep = index === this.props.alphlaValues.length - 1 ? -1 : 1;
                        this.setState({ alpha: this.props.alphlaValues[index + this._alphaStep] });
                    }
                } else {
                    this.setState({ color: item.color });
                    this._colorChanged = true;
                }
            }}
        >
            {this.state.color !== item.color && this.props.strokeComponent && this.props.strokeComponent(item.color)}
            {this.state.color === item.color &&
                this.props.strokeSelectedComponent &&
                this.props.strokeSelectedComponent(item.color + this.state.alpha, index, this._colorChanged)}
        </TouchableOpacity>
    );

    componentDidUpdate() {
        this._colorChanged = false;
    }

    async componentDidMount() {
        const isStoragePermissionAuthorized = await requestPermissions(
            this.props.permissionDialogTitle,
            this.props.permissionDialogMessage
        );
    }




    toggleVisibility = () => {
        this.setState(prevState => ({
            isVisible: !prevState.isVisible,
        }));
    };

    erase_toggle_check = () => {
        //console.log(this.state.erase_toggle);
      
        //eraser mode
        if (!this.state.erase_toggle) {
          this.setState({ before_erase_color: this.state.color });
          this.setState({ color: "#00000000" });

        //brush mode
        } else {

            if(this.state.before_erase_color == ""){
                
                this.setState(
                    {
                      before_erase_color: this.state.color,
                    },
                    () => {
                        this.setState({ color: this.state.before_erase_color });
                    }
                  );
            }
            else{
                this.setState({ color: this.state.before_erase_color });
            }
        }
      };
      
      brush_click = () => {
        this.setState(
          {
            erase_toggle: true,
          },
          () => {
            this.erase_toggle_check();
          }
        );
      };
      
      erase_click = () => {
        this.setState(
          {
            erase_toggle: false,
          },
          () => {
            this.erase_toggle_check();
          }
        );
      };
      


    onColorChange = (color) => {

        const tmp = this.state.color.substring(7, 9);
        //console.log(tmp);


        this.setState({ pick_color: color });

        this.setState({ color: this.state.pick_color + tmp });



    };



    handleColorChange = (color) => {
        this.setState({ colorful: color });
        //this.setState({ color: this.state.colorful });
    };

    handleStrokeWidthChange = (value) => {
        this.setState({ strokeWidth: value });
    };


    test_value_change = (value) => {


        const tmp = (value) => {
            const hexString = value.toString(16);
            return hexString;
        };

        const values = tmp(value);

        //console.log(values);

        this.setState({ color: this.state.color.substring(0, 7) + values });

        //console.log(this.state.color);
    };
    render() {
        const { isVisible } = this.state;
        const { pick_color } = this.state;
        const { colorful } = this.state;
        const { test_value } = this.state;

        const { before_erase_color } = this.state;
        const { erase_toggle } = this.state;

        return (
            <View style={this.props.containerStyle}>

                <View style={styles.navigator_bar}>

                </View>

                <View style={{ flexDirection: "row", backgroundColor: "lightgreen" }}>


                    <View style={{ flexDirection: "row", justifyContent: "flex-start", backgroundColor: 'red' }}>
                        {this.props.closeComponent && (
                            <TouchableOpacity
                                onPress={() => {
                                    this.props.onClosePressed();
                                }}
                            >
                                {this.props.closeComponent}
                            </TouchableOpacity>
                        )}


                        <View style={styles.container} >
                            <Button title={'Eraser'} onPress={this.erase_click} disabled={!this.state.erase_toggle}/>

                        </View>


                        <View style={styles.container}>
                            <Button title={'brush'} onPress={this.brush_click} disabled={this.state.erase_toggle} />

                        </View>



                    </View>





                    <View style={{ flexDirection: "row", justifyContent: "flex-end", marginLeft: 'auto' }}>

                        {this.props.strokeWidthComponent && (
                            <Slider
                                style={styles.slider}
                                minimumValue={this.props.minStrokeWidth}
                                maximumValue={this.props.maxStrokeWidth}
                                step={this.props.strokeWidthStep}
                                value={this.state.strokeWidth}
                                onValueChange={this.handleStrokeWidthChange} // Add an onValueChange callback
                            />
                        )}


                        <Slider
                            style={styles.slider}
                            minimumValue={17}
                            maximumValue={255}
                            step={1}
                            value={this.state.test_value}
                            onValueChange={this.test_value_change}
                        />


                        <View style={styles.container}>
                            <Button title={isVisible ? 'Hide' : 'Show'} onPress={this.toggleVisibility} />
                            {/* {isVisible && (
                            <View style={styles.component}>
                                
                            
                                <SafeAreaView>
                                    <View style={styles.sectionContainer}>
                                        <ColorPicker
                                            color={pick_color}
                                            onColorChange={this.onColorChange}
                                            //onColorChangeComplete={(color) => alert(`Color selected: ${color}`)}
                                            thumbSize={30}
                                            sliderSize={30}
                                            noSnap={true}
                                            row={false}
                                        />
                                    </View>
                                </SafeAreaView>



                            </View>
                        )} */}
                        </View>



                        {this.props.undoComponent && (
                            <TouchableOpacity
                                onPress={() => {
                                    this.props.onUndoPressed(this.undo());
                                }}
                            >
                                {this.props.undoComponent}
                            </TouchableOpacity>
                        )}

                        {this.props.clearComponent && (
                            <TouchableOpacity
                                onPress={() => {
                                    this.clear();
                                    this.props.onClearPressed();
                                }}
                            >
                                {this.props.clearComponent}
                            </TouchableOpacity>
                        )}

                        {this.props.saveComponent && (
                            <TouchableOpacity
                                onPress={() => {
                                    this.save();
                                }}
                            >
                                {this.props.saveComponent}
                            </TouchableOpacity>
                        )}
                    </View>


                </View>

                <SketchCanvas
                    ref={(ref) => (this._sketchCanvas = ref)}
                    style={this.props.canvasStyle}
                    strokeColor={this.state.color + (this.state.color.length === 9 ? "" : this.state.alpha)}
                    onStrokeStart={this.props.onStrokeStart}
                    onStrokeChanged={this.props.onStrokeChanged}
                    onStrokeEnd={this.props.onStrokeEnd}
                    user={this.props.user}
                    strokeWidth={this.state.strokeWidth}
                    onSketchSaved={(success, path) => this.props.onSketchSaved(success, path)}
                    onPathsChange={this.props.onPathsChange}
                    localSourceImage={this.props.localSourceImage}
                    permissionDialogTitle={this.props.permissionDialogTitle}
                    permissionDialogMessage={this.props.permissionDialogMessage}


                />

                <View style={styles.anotherView}>
                    {isVisible && (
                        <View style={styles.component}>
                            <SafeAreaView>
                                <View style={styles.sectionContainer}>
                                    <ColorPicker
                                        color={pick_color}
                                        onColorChange={this.onColorChange}
                                        //onColorChangeComplete={(color) => alert(`Color selected: ${color}`)}
                                        thumbSize={30}
                                        sliderSize={30}
                                        noSnap={true}
                                        row={false}
                                    />
                                </View>
                            </SafeAreaView>
                        </View>
                    )}
                </View>


            </View>
        );
    }
}

const styles = StyleSheet.create({
    navigator_bar :{
        justifyContent: 'center',
        alignItems: 'center',
        backgroundColor: 'gray',
    },

    container: {
        justifyContent: 'center',
        alignItems: 'center',
        backgroundColor: 'blue',
        width: 'auto'
    },
    anotherView: {
        marginTop: 20,
        zIndex: 1,
        position: 'absolute',
        top: 30,
        right: 160
    },
    component: {
        backgroundColor: 'lightblue',
        borderRadius: 10,
        maxWidth: 200,

    },
    sectionContainer: {
        overflow: 'scroll',
    },
    slider: {
        marginRight: 10,
        width: 150
    }
});

RNSketchCanvas.MAIN_BUNDLE = SketchCanvas.MAIN_BUNDLE;
RNSketchCanvas.DOCUMENT = SketchCanvas.DOCUMENT;
RNSketchCanvas.LIBRARY = SketchCanvas.LIBRARY;
RNSketchCanvas.CACHES = SketchCanvas.CACHES;
RNSketchCanvas.TEMPORARY = SketchCanvas.TEMPORARY;
RNSketchCanvas.ROAMING = SketchCanvas.ROAMING;
RNSketchCanvas.LOCAL = SketchCanvas.LOCAL;

export { SketchCanvas };