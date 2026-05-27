package org.zanata.spring.vaadin.theme;

/**
 * Contains the definition for all the CSS utility classes provided by Aura.
 * <p>
 * Project-owned utility class catalog modelled after Vaadin's upstream utility
 * classes. Every CSS class name shipped here is served by
 * {@code aura-utilities.css}; the value strings carry the {@code aura-} prefix
 * so they cannot collide with any vendor classes that may still be present on
 * the page. Nested class layout matches the upstream catalog 1:1 so existing
 * call sites compile unchanged.
 */
public final class AuraUtility {

    private AuraUtility() {
    }

    /**
     * Accessibility related classes.
     */
    public static final class Accessibility {

        /**
         * Hides content visually while keeping it available to screen readers.
         */
        public static final String SCREEN_READER_ONLY = "aura-sr-only";

        private Accessibility() {
        }

    }

    /**
     * Classes for distributing space around and between items along a flexbox’s
     * cross axis or a grid’s block axis. Applies to flexbox and grid layouts.
     */
    public static final class AlignContent {

        public static final String AROUND = "aura-content-around";
        public static final String BETWEEN = "aura-content-between";
        public static final String CENTER = "aura-content-center";
        public static final String END = "aura-content-end";
        public static final String EVENLY = "aura-content-evenly";
        public static final String START = "aura-content-start";
        public static final String STRETCH = "aura-content-stretch";

        private AlignContent() {
        }

    }

    /**
     * Classes for aligning items along a flexbox’s cross axis or a grid’s block
     * axis. Applies to flexbox and grid layouts.
     */
    public static final class AlignItems {
        public static final String BASELINE = "aura-items-baseline";
        public static final String CENTER = "aura-items-center";
        public static final String END = "aura-items-end";
        public static final String START = "aura-items-start";
        public static final String STRETCH = "aura-items-stretch";

        private AlignItems() {
        }

        /**
         * Set of classes for aligning items along a flexbox’s cross axis or a
         * grid’s block axis that will be applied only for certain viewport
         * sizes. Applies to flexbox and grid layouts.
         */
        public static final class Breakpoint {
            private Breakpoint() {
            }

            /**
             * Classes for aligning items along a flexbox’s cross axis or a
             * grid’s block axis that will be applied when the viewport has a
             * minimum width of 640px.
             */
            public static final class Small {
                public static final String BASELINE = "aura-sm:items-baseline";
                public static final String CENTER = "aura-sm:items-center";
                public static final String END = "aura-sm:items-end";
                public static final String START = "aura-sm:items-start";
                public static final String STRETCH = "aura-sm:items-stretch";

                private Small() {
                }
            }

            /**
             * Classes for aligning items along a flexbox’s cross axis or a
             * grid’s block axis that will be applied when the viewport has a
             * minimum width of 768px.
             */
            public static final class Medium {
                public static final String BASELINE = "aura-md:items-baseline";
                public static final String CENTER = "aura-md:items-center";
                public static final String END = "aura-md:items-end";
                public static final String START = "aura-md:items-start";
                public static final String STRETCH = "aura-md:items-stretch";

                private Medium() {
                }
            }

            /**
             * Classes for aligning items along a flexbox’s cross axis or a
             * grid’s block axis that will be applied when the viewport has a
             * minimum width of 1024px.
             */
            public static final class Large {
                public static final String BASELINE = "aura-lg:items-baseline";
                public static final String CENTER = "aura-lg:items-center";
                public static final String END = "aura-lg:items-end";
                public static final String START = "aura-lg:items-start";
                public static final String STRETCH = "aura-lg:items-stretch";

                private Large() {
                }
            }

            /**
             * Classes for aligning items along a flexbox’s cross axis or a
             * grid’s block axis that will be applied when the viewport has a
             * minimum width of 1280px.
             */
            public static final class XLarge {
                public static final String BASELINE = "aura-xl:items-baseline";
                public static final String CENTER = "aura-xl:items-center";
                public static final String END = "aura-xl:items-end";
                public static final String START = "aura-xl:items-start";
                public static final String STRETCH = "aura-xl:items-stretch";

                private XLarge() {
                }
            }

            /**
             * Classes for aligning items along a flexbox’s cross axis or a
             * grid’s block axis that will be applied when the viewport has a
             * minimum width of 1536px.
             */
            public static final class XXLarge {
                public static final String BASELINE = "aura-2xl:items-baseline";
                public static final String CENTER = "aura-2xl:items-center";
                public static final String END = "aura-2xl:items-end";
                public static final String START = "aura-2xl:items-start";
                public static final String STRETCH = "aura-2xl:items-stretch";

                private XXLarge() {
                }
            }
        }

    }

    /**
     * Classes for overriding individual items' align-item property. Applies to
     * flexbox and grid items.
     */
    public static final class AlignSelf {

        public static final String AUTO = "aura-self-auto";
        public static final String BASELINE = "aura-self-baseline";
        public static final String CENTER = "aura-self-center";
        public static final String END = "aura-self-end";
        public static final String START = "aura-self-start";
        public static final String STRETCH = "aura-self-stretch";

        private AlignSelf() {
        }

    }

    /**
     * Classes for setting the aspect ratio of an element.
     */
    public static final class AspectRatio {
        public static final String SQUARE = "aura-aspect-square";
        public static final String VIDEO = "aura-aspect-video";

        private AspectRatio() {

        }
    }

    /**
     * Classes for setting the backdrop blur of an element.
     */
    public static final class BackdropBlur {
        public static final String NONE = "aura-backdrop-blur-none";
        public static final String SMALL = "aura-backdrop-blur-sm";
        public static final String DEFAULT = "aura-backdrop-blur";
        public static final String MEDIUM = "aura-backdrop-blur-md";
        public static final String LARGE = "aura-backdrop-blur-lg";
        public static final String XLARGE = "aura-backdrop-blur-xl";
        public static final String XXLARGE = "aura-backdrop-blur-2xl";
        public static final String XXXLARGE = "aura-backdrop-blur-3xl";

        private BackdropBlur() {

        }
    }

    /**
     * Classes for applying a background color.
     */
    public static final class Background {

        public static final String BASE = "aura-bg-base";
        public static final String TRANSPARENT = "aura-bg-transparent";

        public static final String CONTRAST = "aura-bg-contrast";
        public static final String CONTRAST_90 = "aura-bg-contrast-90";
        public static final String CONTRAST_80 = "aura-bg-contrast-80";
        public static final String CONTRAST_70 = "aura-bg-contrast-70";
        public static final String CONTRAST_60 = "aura-bg-contrast-60";
        public static final String CONTRAST_50 = "aura-bg-contrast-50";
        public static final String CONTRAST_40 = "aura-bg-contrast-40";
        public static final String CONTRAST_30 = "aura-bg-contrast-30";
        public static final String CONTRAST_20 = "aura-bg-contrast-20";
        public static final String CONTRAST_10 = "aura-bg-contrast-10";
        public static final String CONTRAST_5 = "aura-bg-contrast-5";

        public static final String TINT = "aura-bg-tint";
        public static final String TINT_90 = "aura-bg-tint-90";
        public static final String TINT_80 = "aura-bg-tint-80";
        public static final String TINT_70 = "aura-bg-tint-70";
        public static final String TINT_60 = "aura-bg-tint-60";
        public static final String TINT_50 = "aura-bg-tint-50";
        public static final String TINT_40 = "aura-bg-tint-40";
        public static final String TINT_30 = "aura-bg-tint-30";
        public static final String TINT_20 = "aura-bg-tint-20";
        public static final String TINT_10 = "aura-bg-tint-10";
        public static final String TINT_5 = "aura-bg-tint-5";

        public static final String SHADE = "aura-bg-shade";
        public static final String SHADE_90 = "aura-bg-shade-90";
        public static final String SHADE_80 = "aura-bg-shade-80";
        public static final String SHADE_70 = "aura-bg-shade-70";
        public static final String SHADE_60 = "aura-bg-shade-60";
        public static final String SHADE_50 = "aura-bg-shade-50";
        public static final String SHADE_40 = "aura-bg-shade-40";
        public static final String SHADE_30 = "aura-bg-shade-30";
        public static final String SHADE_20 = "aura-bg-shade-20";
        public static final String SHADE_10 = "aura-bg-shade-10";
        public static final String SHADE_5 = "aura-bg-shade-5";

        public static final String PRIMARY = "aura-bg-primary";
        public static final String PRIMARY_50 = "aura-bg-primary-50";
        public static final String PRIMARY_10 = "aura-bg-primary-10";

        public static final String ERROR = "aura-bg-error";
        public static final String ERROR_50 = "aura-bg-error-50";
        public static final String ERROR_10 = "aura-bg-error-10";

        public static final String WARNING = "aura-bg-warning";
        public static final String WARNING_10 = "aura-bg-warning-10";

        public static final String SUCCESS = "aura-bg-success";
        public static final String SUCCESS_50 = "aura-bg-success-50";
        public static final String SUCCESS_10 = "aura-bg-success-10";

        private Background() {
        }

    }

    /**
     * Border-related classes.
     */
    public static final class Border {

        public static final String NONE = "aura-border-0";
        public static final String DASHED = "aura-border-dashed";
        public static final String DOTTED = "aura-border-dotted";
        public static final String ALL = "aura-border";
        public static final String BOTTOM = "aura-border-b";
        public static final String END = "aura-border-e";
        public static final String LEFT = "aura-border-l";
        public static final String RIGHT = "aura-border-r";
        public static final String START = "aura-border-s";
        public static final String TOP = "aura-border-t";

        private Border() {
        }

    }

    /**
     * Classes for setting the border color of an element.
     *
     * <p>The {@link #DEFAULT} and {@link #SECONDARY} variants reference the
     * Vaadin built-in border-color tokens directly — preferred over Lumo's
     * contrast-shade scale because they respect theme overrides (including
     * forced-colors / high-contrast mode) without us having to keep a parallel
     * shade scale in sync.</p>
     */
    public static final class BorderColor {

        /** {@code var(--vaadin-border-color)} — above 3:1 contrast. */
        public static final String DEFAULT = "aura-border-default";

        /** {@code var(--vaadin-border-color-secondary)} — softer hairline. */
        public static final String SECONDARY = "aura-border-secondary";

        public static final String PRIMARY = "aura-border-primary";
        public static final String PRIMARY_50 = "aura-border-primary-50";
        public static final String PRIMARY_10 = "aura-border-primary-10";

        public static final String ERROR = "aura-border-error";
        public static final String ERROR_50 = "aura-border-error-50";
        public static final String ERROR_10 = "aura-border-error-10";

        public static final String WARNING = "aura-border-warning";
        public static final String WARNING_10 = "aura-border-warning-10";
        public static final String WARNING_STRONG = "aura-border-warning-strong";

        public static final String SUCCESS = "aura-border-success";
        public static final String SUCCESS_50 = "aura-border-success-50";
        public static final String SUCCESS_10 = "aura-border-success-10";

        private BorderColor() {
        }

    }

    /**
     * Classes for setting the border radius of an element.
     */
    public static final class BorderRadius {

        public static final String NONE = "aura-rounded-none";
        public static final String SMALL = "aura-rounded-s";
        public static final String MEDIUM = "aura-rounded-m";
        public static final String LARGE = "aura-rounded-l";
        public static final String FULL = "aura-rounded-full";

        private BorderRadius() {
        }

    }

    /**
     * Classes for applying a box shadow.
     */
    public static final class BoxShadow {

        public static final String NONE = "aura-shadow-none";
        public static final String XSMALL = "aura-shadow-xs";
        public static final String SMALL = "aura-shadow-s";
        public static final String MEDIUM = "aura-shadow-m";
        public static final String LARGE = "aura-shadow-l";
        public static final String XLARGE = "aura-shadow-xl";

        private BoxShadow() {
        }

    }

    /**
     * Classes for setting the box sizing property of an element. Box sizing
     * determines whether an element’s border and padding is considered a part
     * of its size.
     */
    public static final class BoxSizing {

        public static final String BORDER = "aura-box-border";
        public static final String CONTENT = "aura-box-content";

        private BoxSizing() {
        }

    }

    /**
     * Classes for setting the display property of an element. Determines
     * whether the element is a block or inline element and how its items are
     * laid out.
     */
    public static final class Display {

        public static final String BLOCK = "aura-block";
        public static final String FLEX = "aura-flex";
        public static final String GRID = "aura-grid";
        public static final String HIDDEN = "aura-hidden";
        public static final String INLINE = "aura-inline";
        public static final String INLINE_BLOCK = "aura-inline-block";
        public static final String INLINE_FLEX = "aura-inline-flex";
        public static final String INLINE_GRID = "aura-inline-grid";

        private Display() {
        }

        /**
         * Set of classes defining the display property of an element that will
         * be applied only for certain viewport sizes.
         */
        public static final class Breakpoint {

            private Breakpoint() {
            }

            /**
             * Classes for defining the display property of an element that will
             * be applied when the viewport has a minimum width of 640px.
             */
            public static final class Small {

                public static final String BLOCK = "aura-sm:block";
                public static final String FLEX = "aura-sm:flex";
                public static final String GRID = "aura-sm:grid";
                public static final String HIDDEN = "aura-sm:hidden";
                public static final String INLINE = "aura-sm:inline";
                public static final String INLINE_BLOCK = "aura-sm:inline-block";
                public static final String INLINE_FLEX = "aura-sm:inline-flex";
                public static final String INLINE_GRID = "aura-sm:inline-grid";

                private Small() {
                }
            }

            /**
             * Classes for defining the display property of an element that will
             * be applied when the viewport has a minimum width of 768px.
             */
            public static final class Medium {

                public static final String BLOCK = "aura-md:block";
                public static final String FLEX = "aura-md:flex";
                public static final String GRID = "aura-md:grid";
                public static final String HIDDEN = "aura-md:hidden";
                public static final String INLINE = "aura-md:inline";
                public static final String INLINE_BLOCK = "aura-md:inline-block";
                public static final String INLINE_FLEX = "aura-md:inline-flex";
                public static final String INLINE_GRID = "aura-md:inline-grid";

                private Medium() {
                }
            }

            /**
             * Classes for defining the display property of an element that will
             * be applied when the viewport has a minimum width of 1024px.
             */
            public static final class Large {

                public static final String BLOCK = "aura-lg:block";
                public static final String FLEX = "aura-lg:flex";
                public static final String GRID = "aura-lg:grid";
                public static final String HIDDEN = "aura-lg:hidden";
                public static final String INLINE = "aura-lg:inline";
                public static final String INLINE_BLOCK = "aura-lg:inline-block";
                public static final String INLINE_FLEX = "aura-lg:inline-flex";
                public static final String INLINE_GRID = "aura-lg:inline-grid";

                private Large() {
                }
            }

            /**
             * Classes for defining the display property of an element that will
             * be applied when the viewport has a minimum width of 1280px.
             */
            public static final class XLarge {

                public static final String BLOCK = "aura-xl:block";
                public static final String FLEX = "aura-xl:flex";
                public static final String GRID = "aura-xl:grid";
                public static final String HIDDEN = "aura-xl:hidden";
                public static final String INLINE = "aura-xl:inline";
                public static final String INLINE_BLOCK = "aura-xl:inline-block";
                public static final String INLINE_FLEX = "aura-xl:inline-flex";
                public static final String INLINE_GRID = "aura-xl:inline-grid";

                private XLarge() {
                }
            }

            /**
             * Classes for defining the display property of an element that will
             * be applied when the viewport has a minimum width of 1536px.
             */
            public static final class XXLarge {

                public static final String BLOCK = "aura-2xl:block";
                public static final String FLEX = "aura-2xl:flex";
                public static final String GRID = "aura-2xl:grid";
                public static final String HIDDEN = "aura-2xl:hidden";
                public static final String INLINE = "aura-2xl:inline";
                public static final String INLINE_BLOCK = "aura-2xl:inline-block";
                public static final String INLINE_FLEX = "aura-2xl:inline-flex";
                public static final String INLINE_GRID = "aura-2xl:inline-grid";

                private XXLarge() {
                }
            }
        }
    }

    /**
     * Classes for setting borders between elements.
     */
    public static final class Divide {
        public static final String X = "aura-divide-x";
        public static final String Y = "aura-divide-y";

        private Divide() {

        }
    }

    /**
     * Classes for setting how items grow and shrink in a flexbox layout.
     * Applies to flexbox items.
     */
    public static final class Flex {

        public static final String ONE = "aura-flex-1";
        public static final String AUTO = "aura-flex-auto";
        public static final String NONE = "aura-flex-none";

        public static final String GROW = "aura-flex-grow";
        public static final String GROW_NONE = "aura-flex-grow-0";

        public static final String SHRINK = "aura-flex-shrink";
        public static final String SHRINK_NONE = "aura-flex-shrink-0";

        private Flex() {
        }

    }

    /**
     * Classes for setting the flex direction of a flexbox layout.
     */
    public static final class FlexDirection {

        public static final String COLUMN = "aura-flex-col";
        public static final String COLUMN_REVERSE = "aura-flex-col-reverse";
        public static final String ROW = "aura-flex-row";
        public static final String ROW_REVERSE = "aura-flex-row-reverse";

        private FlexDirection() {
        }

        /**
         * Set of classes defining the flex direction of an element that will be
         * applied only for certain viewport sizes.
         */
        public static final class Breakpoint {

            private Breakpoint() {
            }

            /**
             * Classes that will be applied when the viewport has a minimum
             * width of 640px.
             */
            public static final class Small {

                public static final String COLUMN = "aura-sm:flex-col";
                public static final String ROW = "aura-sm:flex-row";

                private Small() {
                }
            }

            /**
             * Classes that will be applied when the viewport has a minimum
             * width of 768px.
             */
            public static final class Medium {

                public static final String COLUMN = "aura-md:flex-col";
                public static final String ROW = "aura-md:flex-row";

                private Medium() {
                }
            }

            /**
             * Classes that will be applied when the viewport has a minimum
             * width of 1024px.
             */
            public static final class Large {

                public static final String COLUMN = "aura-lg:flex-col";
                public static final String ROW = "aura-lg:flex-row";

                private Large() {
                }
            }

            /**
             * Classes that will be applied when the viewport has a minimum
             * width of 1280px.
             */
            public static final class XLarge {

                public static final String COLUMN = "aura-xl:flex-col";
                public static final String ROW = "aura-xl:flex-row";

                private XLarge() {
                }
            }

            /**
             * Classes that will be applied when the viewport has a minimum
             * width of 1536px.
             */
            public static final class XXLarge {

                public static final String COLUMN = "aura-2xl:flex-col";
                public static final String ROW = "aura-2xl:flex-row";

                private XXLarge() {
                }
            }

        }

    }

    /**
     * Classes for setting how items wrap in a flexbox layout. Applies to
     * flexbox layouts.
     */
    public static final class FlexWrap {

        public static final String NOWRAP = "aura-flex-nowrap";
        public static final String WRAP = "aura-flex-wrap";
        public static final String WRAP_REVERSE = "aura-flex-wrap-reverse";

        private FlexWrap() {
        }
    }

    /**
     * Classes for setting the font size of an element.
     */
    public static final class FontSize {

        public static final String XXSMALL = "aura-text-2xs";
        public static final String XSMALL = "aura-text-xs";
        public static final String SMALL = "aura-text-s";
        public static final String MEDIUM = "aura-text-m";
        public static final String LARGE = "aura-text-l";
        public static final String XLARGE = "aura-text-xl";
        /** Preserves the existing AuraUtility constant; maps to {@code aura-text-xxl}
         *  (Lumo names this {@code text-2xl}). The CSS ships both selectors. */
        public static final String XXLARGE = "aura-text-xxl";
        public static final String XXXLARGE = "aura-text-3xl";

        private FontSize() {
        }

        /**
         * Set of classes defining the font size of an element that will be
         * applied only for certain viewport sizes.
         */
        public static final class Breakpoint {

            private Breakpoint() {
            }

            /**
             * Classes that will be applied when the viewport has a minimum
             * width of 640px.
             */
            public static final class Small {

                public static final String XXSMALL = "aura-sm:text-2xs";
                public static final String XSMALL = "aura-sm:text-xs";
                public static final String SMALL = "aura-sm:text-s";
                public static final String MEDIUM = "aura-sm:text-m";
                public static final String LARGE = "aura-sm:text-l";
                public static final String XLARGE = "aura-sm:text-xl";
                public static final String XXLARGE = "aura-sm:text-2xl";
                public static final String XXXLARGE = "aura-sm:text-3xl";

                private Small() {
                }
            }

            /**
             * Classes that will be applied when the viewport has a minimum
             * width of 768px.
             */
            public static final class Medium {

                public static final String XXSMALL = "aura-md:text-2xs";
                public static final String XSMALL = "aura-md:text-xs";
                public static final String SMALL = "aura-md:text-s";
                public static final String MEDIUM = "aura-md:text-m";
                public static final String LARGE = "aura-md:text-l";
                public static final String XLARGE = "aura-md:text-xl";
                public static final String XXLARGE = "aura-md:text-2xl";
                public static final String XXXLARGE = "aura-md:text-3xl";

                private Medium() {
                }
            }

            /**
             * Classes that will be applied when the viewport has a minimum
             * width of 1024px.
             */
            public static final class Large {

                public static final String XXSMALL = "aura-lg:text-2xs";
                public static final String XSMALL = "aura-lg:text-xs";
                public static final String SMALL = "aura-lg:text-s";
                public static final String MEDIUM = "aura-lg:text-m";
                public static final String LARGE = "aura-lg:text-l";
                public static final String XLARGE = "aura-lg:text-xl";
                public static final String XXLARGE = "aura-lg:text-2xl";
                public static final String XXXLARGE = "aura-lg:text-3xl";

                private Large() {
                }
            }

            /**
             * Classes that will be applied when the viewport has a minimum
             * width of 1280px.
             */
            public static final class XLarge {

                public static final String XXSMALL = "aura-xl:text-2xs";
                public static final String XSMALL = "aura-xl:text-xs";
                public static final String SMALL = "aura-xl:text-s";
                public static final String MEDIUM = "aura-xl:text-m";
                public static final String LARGE = "aura-xl:text-l";
                public static final String XLARGE = "aura-xl:text-xl";
                public static final String XXLARGE = "aura-xl:text-2xl";
                public static final String XXXLARGE = "aura-xl:text-3xl";

                private XLarge() {
                }
            }

            /**
             * Classes that will be applied when the viewport has a minimum
             * width of 1536px.
             */
            public static final class XXLarge {

                public static final String XXSMALL = "aura-2xl:text-2xs";
                public static final String XSMALL = "aura-2xl:text-xs";
                public static final String SMALL = "aura-2xl:text-s";
                public static final String MEDIUM = "aura-2xl:text-m";
                public static final String LARGE = "aura-2xl:text-l";
                public static final String XLARGE = "aura-2xl:text-xl";
                public static final String XXLARGE = "aura-2xl:text-2xl";
                public static final String XXXLARGE = "aura-2xl:text-3xl";

                private XXLarge() {
                }
            }
        }
    }

    /**
     * Classes for setting the font weight of an element.
     */
    public static final class FontWeight {

        public static final String THIN = "aura-font-thin";
        public static final String EXTRALIGHT = "aura-font-extralight";
        public static final String LIGHT = "aura-font-light";
        public static final String NORMAL = "aura-font-normal";
        public static final String MEDIUM = "aura-font-medium";
        public static final String SEMIBOLD = "aura-font-semibold";
        public static final String BOLD = "aura-font-bold";
        public static final String EXTRABOLD = "aura-font-extrabold";
        public static final String BLACK = "aura-font-black";

        private FontWeight() {
        }
    }

    /**
     * Classes for setting the font style (italic vs. normal) of an element.
     */
    public static final class FontStyle {

        public static final String NORMAL = "aura-font-style-normal";
        public static final String ITALIC = "aura-italic";

        private FontStyle() {
        }
    }

    /**
     * Classes for setting the text-decoration-line of an element.
     */
    public static final class TextDecoration {

        public static final String NONE = "aura-no-underline";
        public static final String UNDERLINE = "aura-underline";
        public static final String LINE_THROUGH = "aura-line-through";

        private TextDecoration() {
        }
    }

    /**
     * Classes for defining the space between items in a flexbox or grid layout.
     * Applies to flexbox and grid layouts.
     */
    public static final class Gap {

        public static final String XSMALL = "aura-gap-xs";
        public static final String SMALL = "aura-gap-s";
        public static final String MEDIUM = "aura-gap-m";
        public static final String LARGE = "aura-gap-l";
        public static final String XLARGE = "aura-gap-xl";

        private Gap() {
        }

        /**
         * Classes for defining the horizontal space between items in a flexbox
         * or grid layout. Applies to flexbox and grid layouts.
         */
        public static final class Column {

            public static final String XSMALL = "aura-gap-x-xs";
            public static final String SMALL = "aura-gap-x-s";
            public static final String MEDIUM = "aura-gap-x-m";
            public static final String LARGE = "aura-gap-x-l";
            public static final String XLARGE = "aura-gap-x-xl";

            private Column() {
            }
        }

        /**
         * Classes for defining the vertical space between items in a flexbox or
         * grid layout. Applies to flexbox and grid layouts.
         */
        public static final class Row {

            public static final String XSMALL = "aura-gap-y-xs";
            public static final String SMALL = "aura-gap-y-s";
            public static final String MEDIUM = "aura-gap-y-m";
            public static final String LARGE = "aura-gap-y-l";
            public static final String XLARGE = "aura-gap-y-xl";

            private Row() {
            }
        }
    }

    /**
     * Set of classes defining the content flow on a grid layout.
     */
    public static final class Grid {

        /**
         * Items are placed by filling each column in turn, adding new columns
         * as necessary.
         */
        public static final String FLOW_COLUMN = "aura-grid-flow-col";
        /**
         * Items are placed by filling each row in turn, adding new rows as
         * necessary.
         */
        public static final String FLOW_ROW = "aura-grid-flow-row";

        private Grid() {
        }

        /**
         * Classes for setting the number of columns in a grid layout.
         */
        public static final class Column {

            public static final String COLUMNS_1 = "aura-grid-cols-1";
            public static final String COLUMNS_2 = "aura-grid-cols-2";
            public static final String COLUMNS_3 = "aura-grid-cols-3";
            public static final String COLUMNS_4 = "aura-grid-cols-4";
            public static final String COLUMNS_5 = "aura-grid-cols-5";
            public static final String COLUMNS_6 = "aura-grid-cols-6";
            public static final String COLUMNS_7 = "aura-grid-cols-7";
            public static final String COLUMNS_8 = "aura-grid-cols-8";
            public static final String COLUMNS_9 = "aura-grid-cols-9";
            public static final String COLUMNS_10 = "aura-grid-cols-10";
            public static final String COLUMNS_11 = "aura-grid-cols-11";
            public static final String COLUMNS_12 = "aura-grid-cols-12";

            public static final String COLUMN_SPAN_1 = "aura-col-span-1";
            public static final String COLUMN_SPAN_2 = "aura-col-span-2";
            public static final String COLUMN_SPAN_3 = "aura-col-span-3";
            public static final String COLUMN_SPAN_4 = "aura-col-span-4";
            public static final String COLUMN_SPAN_5 = "aura-col-span-5";
            public static final String COLUMN_SPAN_6 = "aura-col-span-6";
            public static final String COLUMN_SPAN_7 = "aura-col-span-7";
            public static final String COLUMN_SPAN_8 = "aura-col-span-8";
            public static final String COLUMN_SPAN_9 = "aura-col-span-9";
            public static final String COLUMN_SPAN_10 = "aura-col-span-10";
            public static final String COLUMN_SPAN_11 = "aura-col-span-11";
            public static final String COLUMN_SPAN_12 = "aura-col-span-12";

            private Column() {
            }

        }

        /**
         * Classes for setting the number of rows in a grid layout.
         */
        public static final class Row {

            public static final String ROWS_1 = "aura-grid-rows-1";
            public static final String ROWS_2 = "aura-grid-rows-2";
            public static final String ROWS_3 = "aura-grid-rows-3";
            public static final String ROWS_4 = "aura-grid-rows-4";
            public static final String ROWS_5 = "aura-grid-rows-5";
            public static final String ROWS_6 = "aura-grid-rows-6";

            public static final String ROW_SPAN_1 = "aura-row-span-1";
            public static final String ROW_SPAN_2 = "aura-row-span-2";
            public static final String ROW_SPAN_3 = "aura-row-span-3";
            public static final String ROW_SPAN_4 = "aura-row-span-4";
            public static final String ROW_SPAN_5 = "aura-row-span-5";
            public static final String ROW_SPAN_6 = "aura-row-span-6";

            private Row() {
            }

        }

        /**
         * Set of classes defining the number of columns in a grid layout that
         * will be applied only for certain viewport sizes.
         */
        public static final class Breakpoint {

            private Breakpoint() {
            }

            /**
             * Classes that will be applied when the viewport has a minimum
             * width of 640px.
             */
            public static final class Small {

                public static final String COLUMNS_1 = "aura-sm:grid-cols-1";
                public static final String COLUMNS_2 = "aura-sm:grid-cols-2";
                public static final String COLUMNS_3 = "aura-sm:grid-cols-3";
                public static final String COLUMNS_4 = "aura-sm:grid-cols-4";
                public static final String COLUMNS_5 = "aura-sm:grid-cols-5";
                public static final String COLUMNS_6 = "aura-sm:grid-cols-6";
                public static final String COLUMNS_7 = "aura-sm:grid-cols-7";
                public static final String COLUMNS_8 = "aura-sm:grid-cols-8";
                public static final String COLUMNS_9 = "aura-sm:grid-cols-9";
                public static final String COLUMNS_10 = "aura-sm:grid-cols-10";
                public static final String COLUMNS_11 = "aura-sm:grid-cols-11";
                public static final String COLUMNS_12 = "aura-sm:grid-cols-12";

                private Small() {
                }
            }

            /**
             * Classes that will be applied when the viewport has a minimum
             * width of 768px.
             */
            public static final class Medium {

                public static final String COLUMNS_1 = "aura-md:grid-cols-1";
                public static final String COLUMNS_2 = "aura-md:grid-cols-2";
                public static final String COLUMNS_3 = "aura-md:grid-cols-3";
                public static final String COLUMNS_4 = "aura-md:grid-cols-4";
                public static final String COLUMNS_5 = "aura-md:grid-cols-5";
                public static final String COLUMNS_6 = "aura-md:grid-cols-6";
                public static final String COLUMNS_7 = "aura-md:grid-cols-7";
                public static final String COLUMNS_8 = "aura-md:grid-cols-8";
                public static final String COLUMNS_9 = "aura-md:grid-cols-9";
                public static final String COLUMNS_10 = "aura-md:grid-cols-10";
                public static final String COLUMNS_11 = "aura-md:grid-cols-11";
                public static final String COLUMNS_12 = "aura-md:grid-cols-12";

                private Medium() {
                }
            }

            /**
             * Classes that will be applied when the viewport has a minimum
             * width of 1024px.
             */
            public static final class Large {

                public static final String COLUMNS_1 = "aura-lg:grid-cols-1";
                public static final String COLUMNS_2 = "aura-lg:grid-cols-2";
                public static final String COLUMNS_3 = "aura-lg:grid-cols-3";
                public static final String COLUMNS_4 = "aura-lg:grid-cols-4";
                public static final String COLUMNS_5 = "aura-lg:grid-cols-5";
                public static final String COLUMNS_6 = "aura-lg:grid-cols-6";
                public static final String COLUMNS_7 = "aura-lg:grid-cols-7";
                public static final String COLUMNS_8 = "aura-lg:grid-cols-8";
                public static final String COLUMNS_9 = "aura-lg:grid-cols-9";
                public static final String COLUMNS_10 = "aura-lg:grid-cols-10";
                public static final String COLUMNS_11 = "aura-lg:grid-cols-11";
                public static final String COLUMNS_12 = "aura-lg:grid-cols-12";

                private Large() {
                }
            }

            /**
             * Classes that will be applied when the viewport has a minimum
             * width of 1280px.
             */
            public static final class XLarge {

                public static final String COLUMNS_1 = "aura-xl:grid-cols-1";
                public static final String COLUMNS_2 = "aura-xl:grid-cols-2";
                public static final String COLUMNS_3 = "aura-xl:grid-cols-3";
                public static final String COLUMNS_4 = "aura-xl:grid-cols-4";
                public static final String COLUMNS_5 = "aura-xl:grid-cols-5";
                public static final String COLUMNS_6 = "aura-xl:grid-cols-6";
                public static final String COLUMNS_7 = "aura-xl:grid-cols-7";
                public static final String COLUMNS_8 = "aura-xl:grid-cols-8";
                public static final String COLUMNS_9 = "aura-xl:grid-cols-9";
                public static final String COLUMNS_10 = "aura-xl:grid-cols-10";
                public static final String COLUMNS_11 = "aura-xl:grid-cols-11";
                public static final String COLUMNS_12 = "aura-xl:grid-cols-12";

                private XLarge() {
                }
            }

            /**
             * Classes that will be applied when the viewport has a minimum
             * width of 1536px.
             */
            public static final class XXLarge {

                public static final String COLUMNS_1 = "aura-2xl:grid-cols-1";
                public static final String COLUMNS_2 = "aura-2xl:grid-cols-2";
                public static final String COLUMNS_3 = "aura-2xl:grid-cols-3";
                public static final String COLUMNS_4 = "aura-2xl:grid-cols-4";
                public static final String COLUMNS_5 = "aura-2xl:grid-cols-5";
                public static final String COLUMNS_6 = "aura-2xl:grid-cols-6";
                public static final String COLUMNS_7 = "aura-2xl:grid-cols-7";
                public static final String COLUMNS_8 = "aura-2xl:grid-cols-8";
                public static final String COLUMNS_9 = "aura-2xl:grid-cols-9";
                public static final String COLUMNS_10 = "aura-2xl:grid-cols-10";
                public static final String COLUMNS_11 = "aura-2xl:grid-cols-11";
                public static final String COLUMNS_12 = "aura-2xl:grid-cols-12";

                private XXLarge() {
                }
            }
        }
    }

    /**
     * Classes for defining the height of an element.
     */
    public static final class Height {

        public static final String NONE = "aura-h-0";
        public static final String XSMALL = "aura-h-xs";
        public static final String SMALL = "aura-h-s";
        public static final String MEDIUM = "aura-h-m";
        public static final String LARGE = "aura-h-l";
        public static final String XLARGE = "aura-h-xl";
        public static final String AUTO = "aura-h-auto";
        public static final String FULL = "aura-h-full";
        public static final String SCREEN = "aura-h-screen";

        private Height() {
        }

    }

    /**
     * Classes for defining the size of elements used as icons.
     */
    public static final class IconSize {

        public static final String SMALL = "aura-icon-s";
        public static final String MEDIUM = "aura-icon-m";
        public static final String LARGE = "aura-icon-l";

        private IconSize() {
        }

    }

    /**
     * Classes for aligning items along a flexbox’s main axis or a grid’s inline
     * axis. Applies to flexbox and grid layouts.
     */
    public static final class JustifyContent {

        public static final String AROUND = "aura-justify-around";
        public static final String BETWEEN = "aura-justify-between";
        public static final String CENTER = "aura-justify-center";
        public static final String END = "aura-justify-end";
        public static final String EVENLY = "aura-justify-evenly";
        public static final String START = "aura-justify-start";

        private JustifyContent() {
        }

    }

    /**
     * Classes for setting the line height of an element.
     */
    public static final class LineHeight {

        public static final String NONE = "aura-leading-none";
        public static final String XSMALL = "aura-leading-xs";
        public static final String SMALL = "aura-leading-s";
        public static final String MEDIUM = "aura-leading-m";

        private LineHeight() {
        }

    }

    /**
     * Class for removing the default look of a list.
     */
    public static final class ListStyleType {

        public static final String NONE = "aura-list-none";

        private ListStyleType() {
        }

    }

    /**
     * Classes for setting the margin of an element.
     */
    public static final class Margin {

        public static final String NONE = "aura-m-0";
        public static final String XSMALL = "aura-m-xs";
        public static final String SMALL = "aura-m-s";
        public static final String MEDIUM = "aura-m-m";
        public static final String LARGE = "aura-m-l";
        public static final String XLARGE = "aura-m-xl";
        public static final String AUTO = "aura-m-auto";

        private Margin() {
        }

        /**
         * Classes for setting the bottom margin of an element.
         */
        public static final class Bottom {

            public static final String NONE = "aura-mb-0";
            public static final String XSMALL = "aura-mb-xs";
            public static final String SMALL = "aura-mb-s";
            public static final String MEDIUM = "aura-mb-m";
            public static final String LARGE = "aura-mb-l";
            public static final String XLARGE = "aura-mb-xl";
            public static final String AUTO = "aura-mb-auto";

            private Bottom() {
            }
        }

        /**
         * Classes for setting the logical inline end margin of an element. The
         * actual physical edge where the styles are applied depends on the text
         * flow of the element.
         */
        public static final class End {

            public static final String NONE = "aura-me-0";
            public static final String XSMALL = "aura-me-xs";
            public static final String SMALL = "aura-me-s";
            public static final String MEDIUM = "aura-me-m";
            public static final String LARGE = "aura-me-l";
            public static final String XLARGE = "aura-me-xl";
            public static final String AUTO = "aura-me-auto";

            private End() {
            }
        }

        /**
         * Classes for setting both the left and the right margins an element.
         */
        public static final class Horizontal {

            public static final String NONE = "aura-mx-0";
            public static final String XSMALL = "aura-mx-xs";
            public static final String SMALL = "aura-mx-s";
            public static final String MEDIUM = "aura-mx-m";
            public static final String LARGE = "aura-mx-l";
            public static final String XLARGE = "aura-mx-xl";
            public static final String AUTO = "aura-mx-auto";

            private Horizontal() {
            }
        }

        /**
         * Classes for setting the left margin of an element.
         */
        public static final class Left {

            public static final String NONE = "aura-ml-0";
            public static final String XSMALL = "aura-ml-xs";
            public static final String SMALL = "aura-ml-s";
            public static final String MEDIUM = "aura-ml-m";
            public static final String LARGE = "aura-ml-l";
            public static final String XLARGE = "aura-ml-xl";
            public static final String AUTO = "aura-ml-auto";

            private Left() {
            }
        }

        /**
         * Classes for setting the right margin of an element.
         */
        public static final class Right {

            public static final String NONE = "aura-mr-0";
            public static final String XSMALL = "aura-mr-xs";
            public static final String SMALL = "aura-mr-s";
            public static final String MEDIUM = "aura-mr-m";
            public static final String LARGE = "aura-mr-l";
            public static final String XLARGE = "aura-mr-xl";
            public static final String AUTO = "aura-mr-auto";

            private Right() {
            }
        }

        /**
         * Classes for setting the logical inline start margin of an element.
         * The actual physical edge where the styles are applied depends on the
         * text flow of the element.
         */
        public static final class Start {

            public static final String NONE = "aura-ms-0";
            public static final String XSMALL = "aura-ms-xs";
            public static final String SMALL = "aura-ms-s";
            public static final String MEDIUM = "aura-ms-m";
            public static final String LARGE = "aura-ms-l";
            public static final String XLARGE = "aura-ms-xl";
            public static final String AUTO = "aura-ms-auto";

            private Start() {
            }
        }

        /**
         * Classes for setting the top margin of an element.
         */
        public static final class Top {

            public static final String NONE = "aura-mt-0";
            public static final String XSMALL = "aura-mt-xs";
            public static final String SMALL = "aura-mt-s";
            public static final String MEDIUM = "aura-mt-m";
            public static final String LARGE = "aura-mt-l";
            public static final String XLARGE = "aura-mt-xl";
            public static final String AUTO = "aura-mt-auto";

            private Top() {
            }
        }

        /**
         * Classes for setting both the top and the bottom margins of an
         * element.
         */
        public static final class Vertical {

            public static final String NONE = "aura-my-0";
            public static final String XSMALL = "aura-my-xs";
            public static final String SMALL = "aura-my-s";
            public static final String MEDIUM = "aura-my-m";
            public static final String LARGE = "aura-my-l";
            public static final String XLARGE = "aura-my-xl";
            public static final String AUTO = "aura-my-auto";

            private Vertical() {
            }
        }

        /**
         * Set of classes defining negative margins for an element.
         */
        public static final class Minus {

            private Minus() {
            }

            /**
             * Classes for setting a negative bottom margin for an element.
             */
            public static final class Bottom {

                public static final String XSMALL = "aura--mb-xs";
                public static final String SMALL = "aura--mb-s";
                public static final String MEDIUM = "aura--mb-m";
                public static final String LARGE = "aura--mb-l";
                public static final String XLARGE = "aura--mb-xl";

                private Bottom() {
                }
            }

            /**
             * Classes for setting a negative logical inline end margin for an
             * element. The actual physical edge where the styles are applied
             * depends on the text flow of the element.
             */
            public static final class End {

                public static final String XSMALL = "aura--me-xs";
                public static final String SMALL = "aura--me-s";
                public static final String MEDIUM = "aura--me-m";
                public static final String LARGE = "aura--me-l";
                public static final String XLARGE = "aura--me-xl";

                private End() {
                }
            }

            /**
             * Classes for setting negative left and right margins for an
             * element.
             */
            public static final class Horizontal {

                public static final String XSMALL = "aura--mx-xs";
                public static final String SMALL = "aura--mx-s";
                public static final String MEDIUM = "aura--mx-m";
                public static final String LARGE = "aura--mx-l";
                public static final String XLARGE = "aura--mx-xl";

                private Horizontal() {
                }
            }

            /**
             * Classes for setting a negative left margin for an element.
             */
            public static final class Left {

                public static final String XSMALL = "aura--ml-xs";
                public static final String SMALL = "aura--ml-s";
                public static final String MEDIUM = "aura--ml-m";
                public static final String LARGE = "aura--ml-l";
                public static final String XLARGE = "aura--ml-xl";

                private Left() {
                }
            }

            /**
             * Classes for setting a negative right margin for an element.
             */
            public static final class Right {

                public static final String XSMALL = "aura--mr-xs";
                public static final String SMALL = "aura--mr-s";
                public static final String MEDIUM = "aura--mr-m";
                public static final String LARGE = "aura--mr-l";
                public static final String XLARGE = "aura--mr-xl";

                private Right() {
                }
            }

            /**
             * Classes for setting a negative logical inline start margin for an
             * element. The actual physical edge where the styles are applied
             * depends on the text flow of the element.
             */
            public static final class Start {

                public static final String XSMALL = "aura--ms-xs";
                public static final String SMALL = "aura--ms-s";
                public static final String MEDIUM = "aura--ms-m";
                public static final String LARGE = "aura--ms-l";
                public static final String XLARGE = "aura--ms-xl";

                private Start() {
                }
            }

            /**
             * Classes for setting a negative top margin for an element.
             */
            public static final class Top {

                public static final String XSMALL = "aura--mt-xs";
                public static final String SMALL = "aura--mt-s";
                public static final String MEDIUM = "aura--mt-m";
                public static final String LARGE = "aura--mt-l";
                public static final String XLARGE = "aura--mt-xl";

                private Top() {
                }
            }

            /**
             * Classes for setting negative top and bottom margins for an
             * element.
             */
            public static final class Vertical {

                public static final String XSMALL = "aura--my-xs";
                public static final String SMALL = "aura--my-s";
                public static final String MEDIUM = "aura--my-m";
                public static final String LARGE = "aura--my-l";
                public static final String XLARGE = "aura--my-xl";

                private Vertical() {
                }
            }
        }
    }

    /**
     * Classes for defining the maximum height of an element.
     */
    public static final class MaxHeight {

        public static final String FULL = "aura-max-h-full";
        public static final String SCREEN = "aura-max-h-screen";

        private MaxHeight() {
        }

    }

    /**
     * Classes for defining the maximum width of an element.
     */
    public static final class MaxWidth {

        public static final String FULL = "aura-max-w-full";
        public static final String SCREEN_SMALL = "aura-max-w-screen-sm";
        public static final String SCREEN_MEDIUM = "aura-max-w-screen-md";
        public static final String SCREEN_LARGE = "aura-max-w-screen-lg";
        public static final String SCREEN_XLARGE = "aura-max-w-screen-xl";
        public static final String SCREEN_XXLARGE = "aura-max-w-screen-2xl";

        private MaxWidth() {
        }

    }

    /**
     * Classes for defining the minimum height of an element.
     */
    public static final class MinHeight {

        public static final String NONE = "aura-min-h-0";
        public static final String FULL = "aura-min-h-full";
        public static final String SCREEN = "aura-min-h-screen";

        private MinHeight() {
        }

    }

    /**
     * Classes for defining the minimum width of an element.
     */
    public static final class MinWidth {

        public static final String NONE = "aura-min-w-0";
        public static final String FULL = "aura-min-w-full";

        private MinWidth() {
        }

    }

    /**
     * Classes for setting the overflow behavior of an element.
     */
    public static final class Overflow {

        public static final String AUTO = "aura-overflow-auto";
        public static final String HIDDEN = "aura-overflow-hidden";
        public static final String SCROLL = "aura-overflow-scroll";

        private Overflow() {
        }

        /** Classes for the {@code overflow-x} axis only. */
        public static final class X {
            public static final String AUTO = "aura-overflow-x-auto";
            public static final String HIDDEN = "aura-overflow-x-hidden";
            public static final String SCROLL = "aura-overflow-x-scroll";

            private X() {
            }
        }

        /** Classes for the {@code overflow-y} axis only. */
        public static final class Y {
            public static final String AUTO = "aura-overflow-y-auto";
            public static final String HIDDEN = "aura-overflow-y-hidden";
            public static final String SCROLL = "aura-overflow-y-scroll";

            private Y() {
            }
        }

    }

    /**
     * Classes for setting the mouse cursor of an element. Reference the Vaadin
     * built-in cursor tokens so themes can override globally.
     */
    public static final class Cursor {

        /** {@code var(--vaadin-clickable-cursor)} — defaults to {@code pointer}. */
        public static final String POINTER = "aura-cursor-pointer";
        /** {@code var(--vaadin-disabled-cursor)} — defaults to {@code not-allowed}. */
        public static final String NOT_ALLOWED = "aura-cursor-not-allowed";
        public static final String DEFAULT = "aura-cursor-default";
        public static final String TEXT = "aura-cursor-text";
        public static final String WAIT = "aura-cursor-wait";

        private Cursor() {
        }

    }

    /**
     * Classes for setting the padding of an element.
     */
    public static final class Padding {

        public static final String NONE = "aura-p-0";
        public static final String XSMALL = "aura-p-xs";
        public static final String SMALL = "aura-p-s";
        public static final String MEDIUM = "aura-p-m";
        public static final String LARGE = "aura-p-l";
        public static final String XLARGE = "aura-p-xl";

        private Padding() {
        }

        /**
         * Classes for setting the bottom padding of an element.
         */
        public static final class Bottom {

            public static final String NONE = "aura-pb-0";
            public static final String XSMALL = "aura-pb-xs";
            public static final String SMALL = "aura-pb-s";
            public static final String MEDIUM = "aura-pb-m";
            public static final String LARGE = "aura-pb-l";
            public static final String XLARGE = "aura-pb-xl";

            private Bottom() {
            }
        }

        /**
         * Classes for setting the logical inline end padding of an element. The
         * actual physical edge where the styles are applied depends on the text
         * flow of the element.
         */
        public static final class End {

            public static final String NONE = "aura-pe-0";
            public static final String XSMALL = "aura-pe-xs";
            public static final String SMALL = "aura-pe-s";
            public static final String MEDIUM = "aura-pe-m";
            public static final String LARGE = "aura-pe-l";
            public static final String XLARGE = "aura-pe-xl";

            private End() {
            }
        }

        /**
         * Classes for setting both the right and left paddings of an element.
         */
        public static final class Horizontal {

            public static final String NONE = "aura-px-0";
            public static final String XSMALL = "aura-px-xs";
            public static final String SMALL = "aura-px-s";
            public static final String MEDIUM = "aura-px-m";
            public static final String LARGE = "aura-px-l";
            public static final String XLARGE = "aura-px-xl";

            private Horizontal() {
            }
        }

        /**
         * Classes for setting the left padding of an element.
         */
        public static final class Left {

            public static final String NONE = "aura-pl-0";
            public static final String XSMALL = "aura-pl-xs";
            public static final String SMALL = "aura-pl-s";
            public static final String MEDIUM = "aura-pl-m";
            public static final String LARGE = "aura-pl-l";
            public static final String XLARGE = "aura-pl-xl";

            private Left() {
            }
        }

        /**
         * Classes for setting the right padding of an element.
         */
        public static final class Right {

            public static final String NONE = "aura-pr-0";
            public static final String XSMALL = "aura-pr-xs";
            public static final String SMALL = "aura-pr-s";
            public static final String MEDIUM = "aura-pr-m";
            public static final String LARGE = "aura-pr-l";
            public static final String XLARGE = "aura-pr-xl";

            private Right() {
            }
        }

        /**
         * Classes for setting the logical inline start padding of an element.
         * The actual physical edge where the styles are applied depends on the
         * text flow of the element.
         */
        public static final class Start {

            public static final String NONE = "aura-ps-0";
            public static final String XSMALL = "aura-ps-xs";
            public static final String SMALL = "aura-ps-s";
            public static final String MEDIUM = "aura-ps-m";
            public static final String LARGE = "aura-ps-l";
            public static final String XLARGE = "aura-ps-xl";

            private Start() {
            }
        }

        /**
         * Classes for defining the top padding of an element.
         */
        public static final class Top {

            public static final String NONE = "aura-pt-0";
            public static final String XSMALL = "aura-pt-xs";
            public static final String SMALL = "aura-pt-s";
            public static final String MEDIUM = "aura-pt-m";
            public static final String LARGE = "aura-pt-l";
            public static final String XLARGE = "aura-pt-xl";

            private Top() {
            }
        }

        /**
         * Classes for defining both the vertical and horizontal paddings of an
         * element.
         */
        public static final class Vertical {

            public static final String NONE = "aura-py-0";
            public static final String XSMALL = "aura-py-xs";
            public static final String SMALL = "aura-py-s";
            public static final String MEDIUM = "aura-py-m";
            public static final String LARGE = "aura-py-l";
            public static final String XLARGE = "aura-py-xl";

            private Vertical() {
            }
        }

    }

    /**
     * Classes for setting the position of an element.
     */
    public static final class Position {

        public static final String ABSOLUTE = "aura-absolute";
        public static final String FIXED = "aura-fixed";
        public static final String RELATIVE = "aura-relative";
        public static final String STATIC = "aura-static";
        public static final String STICKY = "aura-sticky";

        private Position() {
        }

        /**
         * Classes for setting the bottom position of an element.
         */
        public static final class Bottom {
            public static final String NONE = "aura-bottom-0";
            public static final String XSMALL = "aura-bottom-xs";
            public static final String SMALL = "aura-bottom-s";
            public static final String MEDIUM = "aura-bottom-m";
            public static final String LARGE = "aura-bottom-l";
            public static final String XLARGE = "aura-bottom-xl";
            public static final String AUTO = "aura-bottom-auto";
            public static final String FULL = "aura-bottom-full";

            private Bottom() {
            }
        }

        /**
         * Classes for setting the end position of an element.
         */
        public static final class End {
            public static final String NONE = "aura-end-0";
            public static final String XSMALL = "aura-end-xs";
            public static final String SMALL = "aura-end-s";
            public static final String MEDIUM = "aura-end-m";
            public static final String LARGE = "aura-end-l";
            public static final String XLARGE = "aura-end-xl";
            public static final String AUTO = "aura-end-auto";
            public static final String FULL = "aura-end-full";

            private End() {
            }
        }

        /**
         * Classes for setting the start position of an element.
         */
        public static final class Start {
            public static final String NONE = "aura-start-0";
            public static final String XSMALL = "aura-start-xs";
            public static final String SMALL = "aura-start-s";
            public static final String MEDIUM = "aura-start-m";
            public static final String LARGE = "aura-start-l";
            public static final String XLARGE = "aura-start-xl";
            public static final String AUTO = "aura-start-auto";
            public static final String FULL = "aura-start-full";

            private Start() {
            }
        }

        /**
         * Classes for setting the top position of an element.
         */
        public static final class Top {
            public static final String NONE = "aura-top-0";
            public static final String XSMALL = "aura-top-xs";
            public static final String SMALL = "aura-top-s";
            public static final String MEDIUM = "aura-top-m";
            public static final String LARGE = "aura-top-l";
            public static final String XLARGE = "aura-top-xl";
            public static final String AUTO = "aura-top-auto";
            public static final String FULL = "aura-top-full";

            private Top() {
            }
        }

        /**
         * Classes for setting a negative position for an element.
         */
        public static final class Minus {

            private Minus() {
            }

            /**
             * Classes for setting a negative bottom position for an element.
             */
            public static final class Bottom {
                public static final String XSMALL = "aura--bottom-xs";
                public static final String SMALL = "aura--bottom-s";
                public static final String MEDIUM = "aura--bottom-m";
                public static final String LARGE = "aura--bottom-l";
                public static final String XLARGE = "aura--bottom-xl";
                public static final String FULL = "aura--bottom-full";

                private Bottom() {
                }
            }

            /**
             * Classes for setting a negative end position for an element.
             */
            public static final class End {
                public static final String XSMALL = "aura--end-xs";
                public static final String SMALL = "aura--end-s";
                public static final String MEDIUM = "aura--end-m";
                public static final String LARGE = "aura--end-l";
                public static final String XLARGE = "aura--end-xl";
                public static final String FULL = "aura--end-full";

                private End() {
                }
            }

            /**
             * Classes for setting a negative start position for an element.
             */
            public static final class Start {
                public static final String XSMALL = "aura--start-xs";
                public static final String SMALL = "aura--start-s";
                public static final String MEDIUM = "aura--start-m";
                public static final String LARGE = "aura--start-l";
                public static final String XLARGE = "aura--start-xl";
                public static final String FULL = "aura--start-full";

                private Start() {
                }
            }

            /**
             * Classes for setting a negative top position for an element.
             */
            public static final class Top {
                public static final String XSMALL = "aura--top-xs";
                public static final String SMALL = "aura--top-s";
                public static final String MEDIUM = "aura--top-m";
                public static final String LARGE = "aura--top-l";
                public static final String XLARGE = "aura--top-xl";
                public static final String FULL = "aura--top-full";

                private Top() {
                }
            }
        }

        /**
         * Set of classes defining the position of an element that will be
         * applied only for certain viewport sizes.
         */
        public static final class Breakpoint {
            private Breakpoint() {
            }

            /**
             * Classes for defining the position of an element that will be
             * applied when the viewport has a minimum width of 640px.
             */
            public static final class Small {

                public static final String ABSOLUTE = "aura-sm:absolute";
                public static final String FIXED = "aura-sm:fixed";
                public static final String RELATIVE = "aura-sm:relative";
                public static final String STATIC = "aura-sm:static";
                public static final String STICKY = "aura-sm:sticky";

                private Small() {
                }
            }

            /**
             * Classes for defining the position of an element that will be
             * applied when the viewport has a minimum width of 768px.
             */
            public static final class Medium {

                public static final String ABSOLUTE = "aura-md:absolute";
                public static final String FIXED = "aura-md:fixed";
                public static final String RELATIVE = "aura-md:relative";
                public static final String STATIC = "aura-md:static";
                public static final String STICKY = "aura-md:sticky";

                private Medium() {
                }
            }

            /**
             * Classes for defining the position of an element that will be
             * applied when the viewport has a minimum width of 1024px.
             */
            public static final class Large {

                public static final String ABSOLUTE = "aura-lg:absolute";
                public static final String FIXED = "aura-lg:fixed";
                public static final String RELATIVE = "aura-lg:relative";
                public static final String STATIC = "aura-lg:static";
                public static final String STICKY = "aura-lg:sticky";

                private Large() {
                }
            }

            /**
             * Classes for defining the position of an element that will be
             * applied when the viewport has a minimum width of 1280px.
             */
            public static final class XLarge {

                public static final String ABSOLUTE = "aura-xl:absolute";
                public static final String FIXED = "aura-xl:fixed";
                public static final String RELATIVE = "aura-xl:relative";
                public static final String STATIC = "aura-xl:static";
                public static final String STICKY = "aura-xl:sticky";

                private XLarge() {
                }
            }

            /**
             * Classes for defining the position of an element that will be
             * applied when the viewport has a minimum width of 1536px.
             */
            public static final class XXLarge {

                public static final String ABSOLUTE = "aura-2xl:absolute";
                public static final String FIXED = "aura-2xl:fixed";
                public static final String RELATIVE = "aura-2xl:relative";
                public static final String STATIC = "aura-2xl:static";
                public static final String STICKY = "aura-2xl:sticky";

                private XXLarge() {
                }
            }
        }
    }

    /**
     * Classes for setting an element’s text alignment.
     */
    public static final class TextAlignment {

        public static final String LEFT = "aura-text-left";
        public static final String CENTER = "aura-text-center";
        public static final String RIGHT = "aura-text-right";
        public static final String JUSTIFY = "aura-text-justify";

        private TextAlignment() {
        }

    }

    /**
     * Classes for setting an element’s text color.
     */
    public static final class TextColor {

        public static final String HEADER = "aura-text-header";
        public static final String BODY = "aura-text-body";
        public static final String SECONDARY = "aura-text-secondary";
        public static final String TERTIARY = "aura-text-tertiary";
        public static final String DISABLED = "aura-text-disabled";

        public static final String PRIMARY = "aura-text-primary";
        public static final String PRIMARY_CONTRAST = "aura-text-primary-contrast";

        public static final String ERROR = "aura-text-error";
        public static final String ERROR_CONTRAST = "aura-text-error-contrast";

        public static final String WARNING = "aura-text-warning";
        public static final String WARNING_CONTRAST = "aura-text-warning-contrast";

        public static final String SUCCESS = "aura-text-success";
        public static final String SUCCESS_CONTRAST = "aura-text-success-contrast";

        /** Mid-state amber/orange. Distinct from {@link #WARNING} (yellow). */
        public static final String ORANGE = "aura-text-orange";

        private TextColor() {
        }

    }

    /**
     * Classes for setting the text overflow.
     */
    public static final class TextOverflow {

        public static final String CLIP = "aura-overflow-clip";
        public static final String ELLIPSIS = "aura-overflow-ellipsis";

        private TextOverflow() {
        }

    }

    /**
     * Classes for transforming the text.
     */
    public static final class TextTransform {

        public static final String CAPITALIZE = "aura-capitalize";
        public static final String LOWERCASE = "aura-lowercase";
        public static final String UPPERCASE = "aura-uppercase";

        private TextTransform() {
        }

    }

    /**
     * Classes for transitioning various properties.
     */
    public static final class Transition {
        public static final String NONE = "aura-transition-none";
        public static final String ALL = "aura-transition-all";
        public static final String DEFAULT = "aura-transition";
        public static final String COLORS = "aura-transition-colors";
        public static final String OPACITY = "aura-transition-opacity";
        public static final String SHADOW = "aura-transition-shadow";
        public static final String TRANSFORM = "aura-transition-transform";

        private Transition() {

        }
    }

    /**
     * Classes for setting how the white space inside an element is handled.
     */
    public static final class Whitespace {

        public static final String NORMAL = "aura-whitespace-normal";
        public static final String NOWRAP = "aura-whitespace-nowrap";
        public static final String PRE = "aura-whitespace-pre";
        public static final String PRE_LINE = "aura-whitespace-pre-line";
        public static final String PRE_WRAP = "aura-whitespace-pre-wrap";

        private Whitespace() {
        }

    }

    /**
     * Classes for setting the width of an element.
     */
    public static final class Width {

        public static final String XSMALL = "aura-w-xs";
        public static final String SMALL = "aura-w-s";
        public static final String MEDIUM = "aura-w-m";
        public static final String LARGE = "aura-w-l";
        public static final String XLARGE = "aura-w-xl";
        public static final String AUTO = "aura-w-auto";
        public static final String FULL = "aura-w-full";

        private Width() {
        }

    }

    /**
     * Classes for setting the z-index of an element.
     */
    public static final class ZIndex {
        public static final String NONE = "aura-z-0";
        public static final String XSMALL = "aura-z-10";
        public static final String SMALL = "aura-z-20";
        public static final String MEDIUM = "aura-z-30";
        public static final String LARGE = "aura-z-40";
        public static final String XLARGE = "aura-z-50";
        public static final String AUTO = "aura-z-auto";

        private ZIndex() {

        }
    }
}
