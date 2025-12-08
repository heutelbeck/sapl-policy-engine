/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.functions.geo;

import lombok.experimental.UtilityClass;

@UtilityClass
public class GeoJSONSchemata {

    public static final String POINT = """
                {
                  "$schema": "http://json-schema.org/draft-07/schema#",
                  "$id": "https://geojson.org/schema/GeoJSON.json",
                  "title": "GeoJSON Point",
                  "type": "object",
                  "required": [
                    "type",
                    "coordinates"
                  ],
                  "properties": {
                    "type": {
                      "type": "string",
                      "enum": [
                        "Point"
                      ]
                    },
                    "coordinates": {
                      "type": "array",
                      "minItems": 2,
                      "items": {
                        "type": "number"
                      }
                    },
                    "bbox": {
                      "type": "array",
                      "minItems": 4,
                      "items": {
                        "type": "number"
                      }
                    }
                  }
                }
            """;

    public static final String POLYGON = """
                {
                  "$schema": "http://json-schema.org/draft-07/schema#",
                  "$id": "https://geojson.org/schema/GeoJSON.json",
                  "title": "GeoJSON Polygon",
                  "type": "object",
                  "required": [
                    "type",
                    "coordinates"
                  ],
                  "properties": {
                    "type": {
                      "type": "string",
                      "enum": [
                        "Polygon"
                      ]
                    },
                    "coordinates": {
                      "type": "array",
                      "items": {
                        "type": "array",
                        "minItems": 4,
                        "items": {
                          "type": "array",
                          "minItems": 2,
                          "items": {
                            "type": "number"
                          }
                        }
                      }
                    },
                    "bbox": {
                      "type": "array",
                      "minItems": 4,
                      "items": {
                        "type": "number"
                      }
                    }
                  }
                }
            """;

    public static final String GEOMETRY_COLLECTION = """
                {
                  "$schema": "http://json-schema.org/draft-07/schema#",
                  "$id": "https://geojson.org/schema/GeoJSON.json",
                  "title": "GeoJSON GeometryCollection",
                  "type": "object",
                  "required": [
                    "type",
                    "geometries"
                  ],
                  "properties": {
                    "type": {
                      "type": "string",
                      "enum": [
                        "GeometryCollection"
                      ]
                    },
                    "geometries": {
                      "type": "array",
                      "items": {
                        "oneOf": [
                          {
                            "title": "GeoJSON Point",
                            "type": "object",
                            "required": [
                              "type",
                              "coordinates"
                            ],
                            "properties": {
                              "type": {
                                "type": "string",
                                "enum": [
                                  "Point"
                                ]
                              },
                              "coordinates": {
                                "type": "array",
                                "minItems": 2,
                                "items": {
                                  "type": "number"
                                }
                              },
                              "bbox": {
                                "type": "array",
                                "minItems": 4,
                                "items": {
                                  "type": "number"
                                }
                              }
                            }
                          },
                          {
                            "title": "GeoJSON LineString",
                            "type": "object",
                            "required": [
                              "type",
                              "coordinates"
                            ],
                            "properties": {
                              "type": {
                                "type": "string",
                                "enum": [
                                  "LineString"
                                ]
                              },
                              "coordinates": {
                                "type": "array",
                                "minItems": 2,
                                "items": {
                                  "type": "array",
                                  "minItems": 2,
                                  "items": {
                                    "type": "number"
                                  }
                                }
                              },
                              "bbox": {
                                "type": "array",
                                "minItems": 4,
                                "items": {
                                  "type": "number"
                                }
                              }
                            }
                          },
                          {
                            "title": "GeoJSON Polygon",
                            "type": "object",
                            "required": [
                              "type",
                              "coordinates"
                            ],
                            "properties": {
                              "type": {
                                "type": "string",
                                "enum": [
                                  "Polygon"
                                ]
                              },
                              "coordinates": {
                                "type": "array",
                                "items": {
                                  "type": "array",
                                  "minItems": 4,
                                  "items": {
                                    "type": "array",
                                    "minItems": 2,
                                    "items": {
                                      "type": "number"
                                    }
                                  }
                                }
                              },
                              "bbox": {
                                "type": "array",
                                "minItems": 4,
                                "items": {
                                  "type": "number"
                                }
                              }
                            }
                          },
                          {
                            "title": "GeoJSON MultiPoint",
                            "type": "object",
                            "required": [
                              "type",
                              "coordinates"
                            ],
                            "properties": {
                              "type": {
                                "type": "string",
                                "enum": [
                                  "MultiPoint"
                                ]
                              },
                              "coordinates": {
                                "type": "array",
                                "items": {
                                  "type": "array",
                                  "minItems": 2,
                                  "items": {
                                    "type": "number"
                                  }
                                }
                              },
                              "bbox": {
                                "type": "array",
                                "minItems": 4,
                                "items": {
                                  "type": "number"
                                }
                              }
                            }
                          },
                          {
                            "title": "GeoJSON MultiLineString",
                            "type": "object",
                            "required": [
                              "type",
                              "coordinates"
                            ],
                            "properties": {
                              "type": {
                                "type": "string",
                                "enum": [
                                  "MultiLineString"
                                ]
                              },
                              "coordinates": {
                                "type": "array",
                                "items": {
                                  "type": "array",
                                  "minItems": 2,
                                  "items": {
                                    "type": "array",
                                    "minItems": 2,
                                    "items": {
                                      "type": "number"
                                    }
                                  }
                                }
                              },
                              "bbox": {
                                "type": "array",
                                "minItems": 4,
                                "items": {
                                  "type": "number"
                                }
                              }
                            }
                          },
                          {
                            "title": "GeoJSON MultiPolygon",
                            "type": "object",
                            "required": [
                              "type",
                              "coordinates"
                            ],
                            "properties": {
                              "type": {
                                "type": "string",
                                "enum": [
                                  "MultiPolygon"
                                ]
                              },
                              "coordinates": {
                                "type": "array",
                                "items": {
                                  "type": "array",
                                  "items": {
                                    "type": "array",
                                    "minItems": 4,
                                    "items": {
                                      "type": "array",
                                      "minItems": 2,
                                      "items": {
                                        "type": "number"
                                      }
                                    }
                                  }
                                }
                              },
                              "bbox": {
                                "type": "array",
                                "minItems": 4,
                                "items": {
                                  "type": "number"
                                }
                              }
                            }
                          }
                        ]
                      }
                    },
                    "bbox": {
                      "type": "array",
                      "minItems": 4,
                      "items": {
                        "type": "number"
                      }
                    }
                  }
                }
            """;

    public static final String CONVEX_HULL = """
            {
               "$schema": "http://json-schema.org/draft-07/schema#",
               "$id": "https://geojson.org/schema/GeoJSON.json",
               "title": "GeoJSON",
               "oneOf": [
                   {
                       "title": "GeoJSON Point",
                       "type": "object",
                       "required": [
                         "type",
                         "coordinates"
                       ],
                       "properties": {
                         "type": {
                           "type": "string",
                           "enum": [
                             "Point"
                           ]
                         },
                         "coordinates": {
                           "type": "array",
                           "minItems": 2,
                           "items": {
                             "type": "number"
                           }
                         },
                         "bbox": {
                           "type": "array",
                           "minItems": 4,
                           "items": {
                             "type": "number"
                           }
                         }
                       }
                     },
                     {
                       "title": "GeoJSON LineString",
                       "type": "object",
                       "required": [
                         "type",
                         "coordinates"
                       ],
                       "properties": {
                         "type": {
                           "type": "string",
                           "enum": [
                             "LineString"
                           ]
                         },
                         "coordinates": {
                           "type": "array",
                           "minItems": 2,
                           "items": {
                             "type": "array",
                             "minItems": 2,
                             "items": {
                               "type": "number"
                             }
                           }
                         },
                         "bbox": {
                           "type": "array",
                           "minItems": 4,
                           "items": {
                             "type": "number"
                           }
                         }
                       }
                     },
                     {
                       "title": "GeoJSON Polygon",
                       "type": "object",
                       "required": [
                         "type",
                         "coordinates"
                       ],
                       "properties": {
                         "type": {
                           "type": "string",
                           "enum": [
                             "Polygon"
                           ]
                         },
                         "coordinates": {
                           "type": "array",
                           "items": {
                             "type": "array",
                             "minItems": 4,
                             "items": {
                               "type": "array",
                               "minItems": 2,
                               "items": {
                                 "type": "number"
                               }
                             }
                           }
                         },
                         "bbox": {
                           "type": "array",
                           "minItems": 4,
                           "items": {
                             "type": "number"
                           }
                         }
                       }
                     },
                     {
                   "title": "GeoJSON GeometryCollection",
                   "type": "object",
                   "required": [
                     "type",
                     "geometries"
                   ],
                   "properties": {
                     "type": {
                       "type": "string",
                       "enum": [
                         "GeometryCollection"
                       ]
                     },
                     "geometries": {
                       "type": "array",
                       "items": {
                         "oneOf": [
                           {
                             "title": "GeoJSON Point",
                             "type": "object",
                             "required": [
                               "type",
                               "coordinates"
                             ],
                             "properties": {
                               "type": {
                                 "type": "string",
                                 "enum": [
                                   "Point"
                                 ]
                               },
                               "coordinates": {
                                 "type": "array",
                                 "minItems": 2,
                                 "items": {
                                   "type": "number"
                                 }
                               },
                               "bbox": {
                                 "type": "array",
                                 "minItems": 4,
                                 "items": {
                                   "type": "number"
                                 }
                               }
                             }
                           },
                           {
                             "title": "GeoJSON LineString",
                             "type": "object",
                             "required": [
                               "type",
                               "coordinates"
                             ],
                             "properties": {
                               "type": {
                                 "type": "string",
                                 "enum": [
                                   "LineString"
                                 ]
                               },
                               "coordinates": {
                                 "type": "array",
                                 "minItems": 2,
                                 "items": {
                                   "type": "array",
                                   "minItems": 2,
                                   "items": {
                                     "type": "number"
                                   }
                                 }
                               },
                               "bbox": {
                                 "type": "array",
                                 "minItems": 4,
                                 "items": {
                                   "type": "number"
                                 }
                               }
                             }
                           },
                           {
                             "title": "GeoJSON Polygon",
                             "type": "object",
                             "required": [
                               "type",
                               "coordinates"
                             ],
                             "properties": {
                               "type": {
                                 "type": "string",
                                 "enum": [
                                   "Polygon"
                                 ]
                               },
                               "coordinates": {
                                 "type": "array",
                                 "items": {
                                   "type": "array",
                                   "minItems": 4,
                                   "items": {
                                     "type": "array",
                                     "minItems": 2,
                                     "items": {
                                       "type": "number"
                                     }
                                   }
                                 }
                               },
                               "bbox": {
                                 "type": "array",
                                 "minItems": 4,
                                 "items": {
                                   "type": "number"
                                 }
                               }
                             }
                           },
                           {
                             "title": "GeoJSON MultiPoint",
                             "type": "object",
                             "required": [
                               "type",
                               "coordinates"
                             ],
                             "properties": {
                               "type": {
                                 "type": "string",
                                 "enum": [
                                   "MultiPoint"
                                 ]
                               },
                               "coordinates": {
                                 "type": "array",
                                 "items": {
                                   "type": "array",
                                   "minItems": 2,
                                   "items": {
                                     "type": "number"
                                   }
                                 }
                               },
                               "bbox": {
                                 "type": "array",
                                 "minItems": 4,
                                 "items": {
                                   "type": "number"
                                 }
                               }
                             }
                           },
                           {
                             "title": "GeoJSON MultiLineString",
                             "type": "object",
                             "required": [
                               "type",
                               "coordinates"
                             ],
                             "properties": {
                               "type": {
                                 "type": "string",
                                 "enum": [
                                   "MultiLineString"
                                 ]
                               },
                               "coordinates": {
                                 "type": "array",
                                 "items": {
                                   "type": "array",
                                   "minItems": 2,
                                   "items": {
                                     "type": "array",
                                     "minItems": 2,
                                     "items": {
                                       "type": "number"
                                     }
                                   }
                                 }
                               },
                               "bbox": {
                                 "type": "array",
                                 "minItems": 4,
                                 "items": {
                                   "type": "number"
                                 }
                               }
                             }
                           },
                           {
                             "title": "GeoJSON MultiPolygon",
                             "type": "object",
                             "required": [
                               "type",
                               "coordinates"
                             ],
                             "properties": {
                               "type": {
                                 "type": "string",
                                 "enum": [
                                   "MultiPolygon"
                                 ]
                               },
                               "coordinates": {
                                 "type": "array",
                                 "items": {
                                   "type": "array",
                                   "items": {
                                     "type": "array",
                                     "minItems": 4,
                                     "items": {
                                       "type": "array",
                                       "minItems": 2,
                                       "items": {
                                         "type": "number"
                                       }
                                     }
                                   }
                                 }
                               },
                               "bbox": {
                                 "type": "array",
                                 "minItems": 4,
                                 "items": {
                                   "type": "number"
                                 }
                               }
                             }
                           }
                         ]
                       }
                     },
                     "bbox": {
                       "type": "array",
                       "minItems": 4,
                       "items": {
                         "type": "number"
                       }
                     }
                   }
                 }
               ]
             }
            """;

    public static final String GEOMETRIES = """
            {
              "$schema": "http://json-schema.org/draft-07/schema#",
              "$id": "https://geojson.org/schema/GeoJSON.json",
              "title": "GeoJSON",
              "oneOf": [
                {
                  "title": "GeoJSON Point",
                  "type": "object",
                  "required": [
                    "type",
                    "coordinates"
                  ],
                  "properties": {
                    "type": {
                      "type": "string",
                      "enum": [
                        "Point"
                      ]
                    },
                    "coordinates": {
                      "type": "array",
                      "minItems": 2,
                      "items": {
                        "type": "number"
                      }
                    },
                    "bbox": {
                      "type": "array",
                      "minItems": 4,
                      "items": {
                        "type": "number"
                      }
                    }
                  }
                },
                {
                  "title": "GeoJSON LineString",
                  "type": "object",
                  "required": [
                    "type",
                    "coordinates"
                  ],
                  "properties": {
                    "type": {
                      "type": "string",
                      "enum": [
                        "LineString"
                      ]
                    },
                    "coordinates": {
                      "type": "array",
                      "minItems": 2,
                      "items": {
                        "type": "array",
                        "minItems": 2,
                        "items": {
                          "type": "number"
                        }
                      }
                    },
                    "bbox": {
                      "type": "array",
                      "minItems": 4,
                      "items": {
                        "type": "number"
                      }
                    }
                  }
                },
                {
                  "title": "GeoJSON Polygon",
                  "type": "object",
                  "required": [
                    "type",
                    "coordinates"
                  ],
                  "properties": {
                    "type": {
                      "type": "string",
                      "enum": [
                        "Polygon"
                      ]
                    },
                    "coordinates": {
                      "type": "array",
                      "items": {
                        "type": "array",
                        "minItems": 4,
                        "items": {
                          "type": "array",
                          "minItems": 2,
                          "items": {
                            "type": "number"
                          }
                        }
                      }
                    },
                    "bbox": {
                      "type": "array",
                      "minItems": 4,
                      "items": {
                        "type": "number"
                      }
                    }
                  }
                },
                {
                  "title": "GeoJSON MultiPoint",
                  "type": "object",
                  "required": [
                    "type",
                    "coordinates"
                  ],
                  "properties": {
                    "type": {
                      "type": "string",
                      "enum": [
                        "MultiPoint"
                      ]
                    },
                    "coordinates": {
                      "type": "array",
                      "items": {
                        "type": "array",
                        "minItems": 2,
                        "items": {
                          "type": "number"
                        }
                      }
                    },
                    "bbox": {
                      "type": "array",
                      "minItems": 4,
                      "items": {
                        "type": "number"
                      }
                    }
                  }
                },
                {
                  "title": "GeoJSON MultiLineString",
                  "type": "object",
                  "required": [
                    "type",
                    "coordinates"
                  ],
                  "properties": {
                    "type": {
                      "type": "string",
                      "enum": [
                        "MultiLineString"
                      ]
                    },
                    "coordinates": {
                      "type": "array",
                      "items": {
                        "type": "array",
                        "minItems": 2,
                        "items": {
                          "type": "array",
                          "minItems": 2,
                          "items": {
                            "type": "number"
                          }
                        }
                      }
                    },
                    "bbox": {
                      "type": "array",
                      "minItems": 4,
                      "items": {
                        "type": "number"
                      }
                    }
                  }
                },
                {
                  "title": "GeoJSON MultiPolygon",
                  "type": "object",
                  "required": [
                    "type",
                    "coordinates"
                  ],
                  "properties": {
                    "type": {
                      "type": "string",
                      "enum": [
                        "MultiPolygon"
                      ]
                    },
                    "coordinates": {
                      "type": "array",
                      "items": {
                        "type": "array",
                        "items": {
                          "type": "array",
                          "minItems": 4,
                          "items": {
                            "type": "array",
                            "minItems": 2,
                            "items": {
                              "type": "number"
                            }
                          }
                        }
                      }
                    },
                    "bbox": {
                      "type": "array",
                      "minItems": 4,
                      "items": {
                        "type": "number"
                      }
                    }
                  }
                }
               ]
              }
            """;

    public static final String JSON_SCHEME_COMPLETE = """
                {
                  "$schema": "http://json-schema.org/draft-07/schema#",
                  "$id": "https://geojson.org/schema/GeoJSON.json",
                  "title": "GeoJSON",
                  "oneOf": [
                    {
                      "title": "GeoJSON Point",
                      "type": "object",
                      "required": [
                        "type",
                        "coordinates"
                      ],
                      "properties": {
                        "type": {
                          "type": "string",
                          "enum": [
                            "Point"
                          ]
                        },
                        "coordinates": {
                          "type": "array",
                          "minItems": 2,
                          "items": {
                            "type": "number"
                          }
                        },
                        "bbox": {
                          "type": "array",
                          "minItems": 4,
                          "items": {
                            "type": "number"
                          }
                        }
                      }
                    },
                    {
                      "title": "GeoJSON LineString",
                      "type": "object",
                      "required": [
                        "type",
                        "coordinates"
                      ],
                      "properties": {
                        "type": {
                          "type": "string",
                          "enum": [
                            "LineString"
                          ]
                        },
                        "coordinates": {
                          "type": "array",
                          "minItems": 2,
                          "items": {
                            "type": "array",
                            "minItems": 2,
                            "items": {
                              "type": "number"
                            }
                          }
                        },
                        "bbox": {
                          "type": "array",
                          "minItems": 4,
                          "items": {
                            "type": "number"
                          }
                        }
                      }
                    },
                    {
                      "title": "GeoJSON Polygon",
                      "type": "object",
                      "required": [
                        "type",
                        "coordinates"
                      ],
                      "properties": {
                        "type": {
                          "type": "string",
                          "enum": [
                            "Polygon"
                          ]
                        },
                        "coordinates": {
                          "type": "array",
                          "items": {
                            "type": "array",
                            "minItems": 4,
                            "items": {
                              "type": "array",
                              "minItems": 2,
                              "items": {
                                "type": "number"
                              }
                            }
                          }
                        },
                        "bbox": {
                          "type": "array",
                          "minItems": 4,
                          "items": {
                            "type": "number"
                          }
                        }
                      }
                    },
                    {
                      "title": "GeoJSON MultiPoint",
                      "type": "object",
                      "required": [
                        "type",
                        "coordinates"
                      ],
                      "properties": {
                        "type": {
                          "type": "string",
                          "enum": [
                            "MultiPoint"
                          ]
                        },
                        "coordinates": {
                          "type": "array",
                          "items": {
                            "type": "array",
                            "minItems": 2,
                            "items": {
                              "type": "number"
                            }
                          }
                        },
                        "bbox": {
                          "type": "array",
                          "minItems": 4,
                          "items": {
                            "type": "number"
                          }
                        }
                      }
                    },
                    {
                      "title": "GeoJSON MultiLineString",
                      "type": "object",
                      "required": [
                        "type",
                        "coordinates"
                      ],
                      "properties": {
                        "type": {
                          "type": "string",
                          "enum": [
                            "MultiLineString"
                          ]
                        },
                        "coordinates": {
                          "type": "array",
                          "items": {
                            "type": "array",
                            "minItems": 2,
                            "items": {
                              "type": "array",
                              "minItems": 2,
                              "items": {
                                "type": "number"
                              }
                            }
                          }
                        },
                        "bbox": {
                          "type": "array",
                          "minItems": 4,
                          "items": {
                            "type": "number"
                          }
                        }
                      }
                    },
                    {
                      "title": "GeoJSON MultiPolygon",
                      "type": "object",
                      "required": [
                        "type",
                        "coordinates"
                      ],
                      "properties": {
                        "type": {
                          "type": "string",
                          "enum": [
                            "MultiPolygon"
                          ]
                        },
                        "coordinates": {
                          "type": "array",
                          "items": {
                            "type": "array",
                            "items": {
                              "type": "array",
                              "minItems": 4,
                              "items": {
                                "type": "array",
                                "minItems": 2,
                                "items": {
                                  "type": "number"
                                }
                              }
                            }
                          }
                        },
                        "bbox": {
                          "type": "array",
                          "minItems": 4,
                          "items": {
                            "type": "number"
                          }
                        }
                      }
                    },
                    {
                      "title": "GeoJSON GeometryCollection",
                      "type": "object",
                      "required": [
                        "type",
                        "geometries"
                      ],
                      "properties": {
                        "type": {
                          "type": "string",
                          "enum": [
                            "GeometryCollection"
                          ]
                        },
                        "geometries": {
                          "type": "array",
                          "items": {
                            "oneOf": [
                              {
                                "title": "GeoJSON Point",
                                "type": "object",
                                "required": [
                                  "type",
                                  "coordinates"
                                ],
                                "properties": {
                                  "type": {
                                    "type": "string",
                                    "enum": [
                                      "Point"
                                    ]
                                  },
                                  "coordinates": {
                                    "type": "array",
                                    "minItems": 2,
                                    "items": {
                                      "type": "number"
                                    }
                                  },
                                  "bbox": {
                                    "type": "array",
                                    "minItems": 4,
                                    "items": {
                                      "type": "number"
                                    }
                                  }
                                }
                              },
                              {
                                "title": "GeoJSON LineString",
                                "type": "object",
                                "required": [
                                  "type",
                                  "coordinates"
                                ],
                                "properties": {
                                  "type": {
                                    "type": "string",
                                    "enum": [
                                      "LineString"
                                    ]
                                  },
                                  "coordinates": {
                                    "type": "array",
                                    "minItems": 2,
                                    "items": {
                                      "type": "array",
                                      "minItems": 2,
                                      "items": {
                                        "type": "number"
                                      }
                                    }
                                  },
                                  "bbox": {
                                    "type": "array",
                                    "minItems": 4,
                                    "items": {
                                      "type": "number"
                                    }
                                  }
                                }
                              },
                              {
                                "title": "GeoJSON Polygon",
                                "type": "object",
                                "required": [
                                  "type",
                                  "coordinates"
                                ],
                                "properties": {
                                  "type": {
                                    "type": "string",
                                    "enum": [
                                      "Polygon"
                                    ]
                                  },
                                  "coordinates": {
                                    "type": "array",
                                    "items": {
                                      "type": "array",
                                      "minItems": 4,
                                      "items": {
                                        "type": "array",
                                        "minItems": 2,
                                        "items": {
                                          "type": "number"
                                        }
                                      }
                                    }
                                  },
                                  "bbox": {
                                    "type": "array",
                                    "minItems": 4,
                                    "items": {
                                      "type": "number"
                                    }
                                  }
                                }
                              },
                              {
                                "title": "GeoJSON MultiPoint",
                                "type": "object",
                                "required": [
                                  "type",
                                  "coordinates"
                                ],
                                "properties": {
                                  "type": {
                                    "type": "string",
                                    "enum": [
                                      "MultiPoint"
                                    ]
                                  },
                                  "coordinates": {
                                    "type": "array",
                                    "items": {
                                      "type": "array",
                                      "minItems": 2,
                                      "items": {
                                        "type": "number"
                                      }
                                    }
                                  },
                                  "bbox": {
                                    "type": "array",
                                    "minItems": 4,
                                    "items": {
                                      "type": "number"
                                    }
                                  }
                                }
                              },
                              {
                                "title": "GeoJSON MultiLineString",
                                "type": "object",
                                "required": [
                                  "type",
                                  "coordinates"
                                ],
                                "properties": {
                                  "type": {
                                    "type": "string",
                                    "enum": [
                                      "MultiLineString"
                                    ]
                                  },
                                  "coordinates": {
                                    "type": "array",
                                    "items": {
                                      "type": "array",
                                      "minItems": 2,
                                      "items": {
                                        "type": "array",
                                        "minItems": 2,
                                        "items": {
                                          "type": "number"
                                        }
                                      }
                                    }
                                  },
                                  "bbox": {
                                    "type": "array",
                                    "minItems": 4,
                                    "items": {
                                      "type": "number"
                                    }
                                  }
                                }
                              },
                              {
                                "title": "GeoJSON MultiPolygon",
                                "type": "object",
                                "required": [
                                  "type",
                                  "coordinates"
                                ],
                                "properties": {
                                  "type": {
                                    "type": "string",
                                    "enum": [
                                      "MultiPolygon"
                                    ]
                                  },
                                  "coordinates": {
                                    "type": "array",
                                    "items": {
                                      "type": "array",
                                      "items": {
                                        "type": "array",
                                        "minItems": 4,
                                        "items": {
                                          "type": "array",
                                          "minItems": 2,
                                          "items": {
                                            "type": "number"
                                          }
                                        }
                                      }
                                    }
                                  },
                                  "bbox": {
                                    "type": "array",
                                    "minItems": 4,
                                    "items": {
                                      "type": "number"
                                    }
                                  }
                                }
                              }
                            ]
                          }
                        },
                        "bbox": {
                          "type": "array",
                          "minItems": 4,
                          "items": {
                            "type": "number"
                          }
                        }
                      }
                    },
                    {
                      "title": "GeoJSON Feature",
                      "type": "object",
                      "required": [
                        "type",
                        "properties",
                        "geometry"
                      ],
                      "properties": {
                        "type": {
                          "type": "string",
                          "enum": [
                            "Feature"
                          ]
                        },
                        "id": {
                          "oneOf": [
                            {
                              "type": "number"
                            },
                            {
                              "type": "string"
                            }
                          ]
                        },
                        "properties": {
                          "oneOf": [
                            {
                              "type": "null"
                            },
                            {
                              "type": "object"
                            }
                          ]
                        },
                        "geometry": {
                          "oneOf": [
                            {
                              "type": "null"
                            },
                            {
                              "title": "GeoJSON Point",
                              "type": "object",
                              "required": [
                                "type",
                                "coordinates"
                              ],
                              "properties": {
                                "type": {
                                  "type": "string",
                                  "enum": [
                                    "Point"
                                  ]
                                },
                                "coordinates": {
                                  "type": "array",
                                  "minItems": 2,
                                  "items": {
                                    "type": "number"
                                  }
                                },
                                "bbox": {
                                  "type": "array",
                                  "minItems": 4,
                                  "items": {
                                    "type": "number"
                                  }
                                }
                              }
                            },
                            {
                              "title": "GeoJSON LineString",
                              "type": "object",
                              "required": [
                                "type",
                                "coordinates"
                              ],
                              "properties": {
                                "type": {
                                  "type": "string",
                                  "enum": [
                                    "LineString"
                                  ]
                                },
                                "coordinates": {
                                  "type": "array",
                                  "minItems": 2,
                                  "items": {
                                    "type": "array",
                                    "minItems": 2,
                                    "items": {
                                      "type": "number"
                                    }
                                  }
                                },
                                "bbox": {
                                  "type": "array",
                                  "minItems": 4,
                                  "items": {
                                    "type": "number"
                                  }
                                }
                              }
                            },
                            {
                              "title": "GeoJSON Polygon",
                              "type": "object",
                              "required": [
                                "type",
                                "coordinates"
                              ],
                              "properties": {
                                "type": {
                                  "type": "string",
                                  "enum": [
                                    "Polygon"
                                  ]
                                },
                                "coordinates": {
                                  "type": "array",
                                  "items": {
                                    "type": "array",
                                    "minItems": 4,
                                    "items": {
                                      "type": "array",
                                      "minItems": 2,
                                      "items": {
                                        "type": "number"
                                      }
                                    }
                                  }
                                },
                                "bbox": {
                                  "type": "array",
                                  "minItems": 4,
                                  "items": {
                                    "type": "number"
                                  }
                                }
                              }
                            },
                            {
                              "title": "GeoJSON MultiPoint",
                              "type": "object",
                              "required": [
                                "type",
                                "coordinates"
                              ],
                              "properties": {
                                "type": {
                                  "type": "string",
                                  "enum": [
                                    "MultiPoint"
                                  ]
                                },
                                "coordinates": {
                                  "type": "array",
                                  "items": {
                                    "type": "array",
                                    "minItems": 2,
                                    "items": {
                                      "type": "number"
                                    }
                                  }
                                },
                                "bbox": {
                                  "type": "array",
                                  "minItems": 4,
                                  "items": {
                                    "type": "number"
                                  }
                                }
                              }
                            },
                            {
                              "title": "GeoJSON MultiLineString",
                              "type": "object",
                              "required": [
                                "type",
                                "coordinates"
                              ],
                              "properties": {
                                "type": {
                                  "type": "string",
                                  "enum": [
                                    "MultiLineString"
                                  ]
                                },
                                "coordinates": {
                                  "type": "array",
                                  "items": {
                                    "type": "array",
                                    "minItems": 2,
                                    "items": {
                                      "type": "array",
                                      "minItems": 2,
                                      "items": {
                                        "type": "number"
                                      }
                                    }
                                  }
                                },
                                "bbox": {
                                  "type": "array",
                                  "minItems": 4,
                                  "items": {
                                    "type": "number"
                                  }
                                }
                              }
                            },
                            {
                              "title": "GeoJSON MultiPolygon",
                              "type": "object",
                              "required": [
                                "type",
                                "coordinates"
                              ],
                              "properties": {
                                "type": {
                                  "type": "string",
                                  "enum": [
                                    "MultiPolygon"
                                  ]
                                },
                                "coordinates": {
                                  "type": "array",
                                  "items": {
                                    "type": "array",
                                    "items": {
                                      "type": "array",
                                      "minItems": 4,
                                      "items": {
                                        "type": "array",
                                        "minItems": 2,
                                        "items": {
                                          "type": "number"
                                        }
                                      }
                                    }
                                  }
                                },
                                "bbox": {
                                  "type": "array",
                                  "minItems": 4,
                                  "items": {
                                    "type": "number"
                                  }
                                }
                              }
                            },
                            {
                              "title": "GeoJSON GeometryCollection",
                              "type": "object",
                              "required": [
                                "type",
                                "geometries"
                              ],
                              "properties": {
                                "type": {
                                  "type": "string",
                                  "enum": [
                                    "GeometryCollection"
                                  ]
                                },
                                "geometries": {
                                  "type": "array",
                                  "items": {
                                    "oneOf": [
                                      {
                                        "title": "GeoJSON Point",
                                        "type": "object",
                                        "required": [
                                          "type",
                                          "coordinates"
                                        ],
                                        "properties": {
                                          "type": {
                                            "type": "string",
                                            "enum": [
                                              "Point"
                                            ]
                                          },
                                          "coordinates": {
                                            "type": "array",
                                            "minItems": 2,
                                            "items": {
                                              "type": "number"
                                            }
                                          },
                                          "bbox": {
                                            "type": "array",
                                            "minItems": 4,
                                            "items": {
                                              "type": "number"
                                            }
                                          }
                                        }
                                      },
                                      {
                                        "title": "GeoJSON LineString",
                                        "type": "object",
                                        "required": [
                                          "type",
                                          "coordinates"
                                        ],
                                        "properties": {
                                          "type": {
                                            "type": "string",
                                            "enum": [
                                              "LineString"
                                            ]
                                          },
                                          "coordinates": {
                                            "type": "array",
                                            "minItems": 2,
                                            "items": {
                                              "type": "array",
                                              "minItems": 2,
                                              "items": {
                                                "type": "number"
                                              }
                                            }
                                          },
                                          "bbox": {
                                            "type": "array",
                                            "minItems": 4,
                                            "items": {
                                              "type": "number"
                                            }
                                          }
                                        }
                                      },
                                      {
                                        "title": "GeoJSON Polygon",
                                        "type": "object",
                                        "required": [
                                          "type",
                                          "coordinates"
                                        ],
                                        "properties": {
                                          "type": {
                                            "type": "string",
                                            "enum": [
                                              "Polygon"
                                            ]
                                          },
                                          "coordinates": {
                                            "type": "array",
                                            "items": {
                                              "type": "array",
                                              "minItems": 4,
                                              "items": {
                                                "type": "array",
                                                "minItems": 2,
                                                "items": {
                                                  "type": "number"
                                                }
                                              }
                                            }
                                          },
                                          "bbox": {
                                            "type": "array",
                                            "minItems": 4,
                                            "items": {
                                              "type": "number"
                                            }
                                          }
                                        }
                                      },
                                      {
                                        "title": "GeoJSON MultiPoint",
                                        "type": "object",
                                        "required": [
                                          "type",
                                          "coordinates"
                                        ],
                                        "properties": {
                                          "type": {
                                            "type": "string",
                                            "enum": [
                                              "MultiPoint"
                                            ]
                                          },
                                          "coordinates": {
                                            "type": "array",
                                            "items": {
                                              "type": "array",
                                              "minItems": 2,
                                              "items": {
                                                "type": "number"
                                              }
                                            }
                                          },
                                          "bbox": {
                                            "type": "array",
                                            "minItems": 4,
                                            "items": {
                                              "type": "number"
                                            }
                                          }
                                        }
                                      },
                                      {
                                        "title": "GeoJSON MultiLineString",
                                        "type": "object",
                                        "required": [
                                          "type",
                                          "coordinates"
                                        ],
                                        "properties": {
                                          "type": {
                                            "type": "string",
                                            "enum": [
                                              "MultiLineString"
                                            ]
                                          },
                                          "coordinates": {
                                            "type": "array",
                                            "items": {
                                              "type": "array",
                                              "minItems": 2,
                                              "items": {
                                                "type": "array",
                                                "minItems": 2,
                                                "items": {
                                                  "type": "number"
                                                }
                                              }
                                            }
                                          },
                                          "bbox": {
                                            "type": "array",
                                            "minItems": 4,
                                            "items": {
                                              "type": "number"
                                            }
                                          }
                                        }
                                      },
                                      {
                                        "title": "GeoJSON MultiPolygon",
                                        "type": "object",
                                        "required": [
                                          "type",
                                          "coordinates"
                                        ],
                                        "properties": {
                                          "type": {
                                            "type": "string",
                                            "enum": [
                                              "MultiPolygon"
                                            ]
                                          },
                                          "coordinates": {
                                            "type": "array",
                                            "items": {
                                              "type": "array",
                                              "items": {
                                                "type": "array",
                                                "minItems": 4,
                                                "items": {
                                                  "type": "array",
                                                  "minItems": 2,
                                                  "items": {
                                                    "type": "number"
                                                  }
                                                }
                                              }
                                            }
                                          },
                                          "bbox": {
                                            "type": "array",
                                            "minItems": 4,
                                            "items": {
                                              "type": "number"
                                            }
                                          }
                                        }
                                      }
                                    ]
                                  }
                                },
                                "bbox": {
                                  "type": "array",
                                  "minItems": 4,
                                  "items": {
                                    "type": "number"
                                  }
                                }
                              }
                            }
                          ]
                        },
                        "bbox": {
                          "type": "array",
                          "minItems": 4,
                          "items": {
                            "type": "number"
                          }
                        }
                      }
                    },
                    {
                      "title": "GeoJSON FeatureCollection",
                      "type": "object",
                      "required": [
                        "type",
                        "features"
                      ],
                      "properties": {
                        "type": {
                          "type": "string",
                          "enum": [
                            "FeatureCollection"
                          ]
                        },
                        "features": {
                          "type": "array",
                          "items": {
                            "title": "GeoJSON Feature",
                            "type": "object",
                            "required": [
                              "type",
                              "properties",
                              "geometry"
                            ],
                            "properties": {
                              "type": {
                                "type": "string",
                                "enum": [
                                  "Feature"
                                ]
                              },
                              "id": {
                                "oneOf": [
                                  {
                                    "type": "number"
                                  },
                                  {
                                    "type": "string"
                                  }
                                ]
                              },
                              "properties": {
                                "oneOf": [
                                  {
                                    "type": "null"
                                  },
                                  {
                                    "type": "object"
                                  }
                                ]
                              },
                              "geometry": {
                                "oneOf": [
                                  {
                                    "type": "null"
                                  },
                                  {
                                    "title": "GeoJSON Point",
                                    "type": "object",
                                    "required": [
                                      "type",
                                      "coordinates"
                                    ],
                                    "properties": {
                                      "type": {
                                        "type": "string",
                                        "enum": [
                                          "Point"
                                        ]
                                      },
                                      "coordinates": {
                                        "type": "array",
                                        "minItems": 2,
                                        "items": {
                                          "type": "number"
                                        }
                                      },
                                      "bbox": {
                                        "type": "array",
                                        "minItems": 4,
                                        "items": {
                                          "type": "number"
                                        }
                                      }
                                    }
                                  },
                                  {
                                    "title": "GeoJSON LineString",
                                    "type": "object",
                                    "required": [
                                      "type",
                                      "coordinates"
                                    ],
                                    "properties": {
                                      "type": {
                                        "type": "string",
                                        "enum": [
                                          "LineString"
                                        ]
                                      },
                                      "coordinates": {
                                        "type": "array",
                                        "minItems": 2,
                                        "items": {
                                          "type": "array",
                                          "minItems": 2,
                                          "items": {
                                            "type": "number"
                                          }
                                        }
                                      },
                                      "bbox": {
                                        "type": "array",
                                        "minItems": 4,
                                        "items": {
                                          "type": "number"
                                        }
                                      }
                                    }
                                  },
                                  {
                                    "title": "GeoJSON Polygon",
                                    "type": "object",
                                    "required": [
                                      "type",
                                      "coordinates"
                                    ],
                                    "properties": {
                                      "type": {
                                        "type": "string",
                                        "enum": [
                                          "Polygon"
                                        ]
                                      },
                                      "coordinates": {
                                        "type": "array",
                                        "items": {
                                          "type": "array",
                                          "minItems": 4,
                                          "items": {
                                            "type": "array",
                                            "minItems": 2,
                                            "items": {
                                              "type": "number"
                                            }
                                          }
                                        }
                                      },
                                      "bbox": {
                                        "type": "array",
                                        "minItems": 4,
                                        "items": {
                                          "type": "number"
                                        }
                                      }
                                    }
                                  },
                                  {
                                    "title": "GeoJSON MultiPoint",
                                    "type": "object",
                                    "required": [
                                      "type",
                                      "coordinates"
                                    ],
                                    "properties": {
                                      "type": {
                                        "type": "string",
                                        "enum": [
                                          "MultiPoint"
                                        ]
                                      },
                                      "coordinates": {
                                        "type": "array",
                                        "items": {
                                          "type": "array",
                                          "minItems": 2,
                                          "items": {
                                            "type": "number"
                                          }
                                        }
                                      },
                                      "bbox": {
                                        "type": "array",
                                        "minItems": 4,
                                        "items": {
                                          "type": "number"
                                        }
                                      }
                                    }
                                  },
                                  {
                                    "title": "GeoJSON MultiLineString",
                                    "type": "object",
                                    "required": [
                                      "type",
                                      "coordinates"
                                    ],
                                    "properties": {
                                      "type": {
                                        "type": "string",
                                        "enum": [
                                          "MultiLineString"
                                        ]
                                      },
                                      "coordinates": {
                                        "type": "array",
                                        "items": {
                                          "type": "array",
                                          "minItems": 2,
                                          "items": {
                                            "type": "array",
                                            "minItems": 2,
                                            "items": {
                                              "type": "number"
                                            }
                                          }
                                        }
                                      },
                                      "bbox": {
                                        "type": "array",
                                        "minItems": 4,
                                        "items": {
                                          "type": "number"
                                        }
                                      }
                                    }
                                  },
                                  {
                                    "title": "GeoJSON MultiPolygon",
                                    "type": "object",
                                    "required": [
                                      "type",
                                      "coordinates"
                                    ],
                                    "properties": {
                                      "type": {
                                        "type": "string",
                                        "enum": [
                                          "MultiPolygon"
                                        ]
                                      },
                                      "coordinates": {
                                        "type": "array",
                                        "items": {
                                          "type": "array",
                                          "items": {
                                            "type": "array",
                                            "minItems": 4,
                                            "items": {
                                              "type": "array",
                                              "minItems": 2,
                                              "items": {
                                                "type": "number"
                                              }
                                            }
                                          }
                                        }
                                      },
                                      "bbox": {
                                        "type": "array",
                                        "minItems": 4,
                                        "items": {
                                          "type": "number"
                                        }
                                      }
                                    }
                                  },
                                  {
                                    "title": "GeoJSON GeometryCollection",
                                    "type": "object",
                                    "required": [
                                      "type",
                                      "geometries"
                                    ],
                                    "properties": {
                                      "type": {
                                        "type": "string",
                                        "enum": [
                                          "GeometryCollection"
                                        ]
                                      },
                                      "geometries": {
                                        "type": "array",
                                        "items": {
                                          "oneOf": [
                                            {
                                              "title": "GeoJSON Point",
                                              "type": "object",
                                              "required": [
                                                "type",
                                                "coordinates"
                                              ],
                                              "properties": {
                                                "type": {
                                                  "type": "string",
                                                  "enum": [
                                                    "Point"
                                                  ]
                                                },
                                                "coordinates": {
                                                  "type": "array",
                                                  "minItems": 2,
                                                  "items": {
                                                    "type": "number"
                                                  }
                                                },
                                                "bbox": {
                                                  "type": "array",
                                                  "minItems": 4,
                                                  "items": {
                                                    "type": "number"
                                                  }
                                                }
                                              }
                                            },
                                            {
                                              "title": "GeoJSON LineString",
                                              "type": "object",
                                              "required": [
                                                "type",
                                                "coordinates"
                                              ],
                                              "properties": {
                                                "type": {
                                                  "type": "string",
                                                  "enum": [
                                                    "LineString"
                                                  ]
                                                },
                                                "coordinates": {
                                                  "type": "array",
                                                  "minItems": 2,
                                                  "items": {
                                                    "type": "array",
                                                    "minItems": 2,
                                                    "items": {
                                                      "type": "number"
                                                    }
                                                  }
                                                },
                                                "bbox": {
                                                  "type": "array",
                                                  "minItems": 4,
                                                  "items": {
                                                    "type": "number"
                                                  }
                                                }
                                              }
                                            },
                                            {
                                              "title": "GeoJSON Polygon",
                                              "type": "object",
                                              "required": [
                                                "type",
                                                "coordinates"
                                              ],
                                              "properties": {
                                                "type": {
                                                  "type": "string",
                                                  "enum": [
                                                    "Polygon"
                                                  ]
                                                },
                                                "coordinates": {
                                                  "type": "array",
                                                  "items": {
                                                    "type": "array",
                                                    "minItems": 4,
                                                    "items": {
                                                      "type": "array",
                                                      "minItems": 2,
                                                      "items": {
                                                        "type": "number"
                                                      }
                                                    }
                                                  }
                                                },
                                                "bbox": {
                                                  "type": "array",
                                                  "minItems": 4,
                                                  "items": {
                                                    "type": "number"
                                                  }
                                                }
                                              }
                                            },
                                            {
                                              "title": "GeoJSON MultiPoint",
                                              "type": "object",
                                              "required": [
                                                "type",
                                                "coordinates"
                                              ],
                                              "properties": {
                                                "type": {
                                                  "type": "string",
                                                  "enum": [
                                                    "MultiPoint"
                                                  ]
                                                },
                                                "coordinates": {
                                                  "type": "array",
                                                  "items": {
                                                    "type": "array",
                                                    "minItems": 2,
                                                    "items": {
                                                      "type": "number"
                                                    }
                                                  }
                                                },
                                                "bbox": {
                                                  "type": "array",
                                                  "minItems": 4,
                                                  "items": {
                                                    "type": "number"
                                                  }
                                                }
                                              }
                                            },
                                            {
                                              "title": "GeoJSON MultiLineString",
                                              "type": "object",
                                              "required": [
                                                "type",
                                                "coordinates"
                                              ],
                                              "properties": {
                                                "type": {
                                                  "type": "string",
                                                  "enum": [
                                                    "MultiLineString"
                                                  ]
                                                },
                                                "coordinates": {
                                                  "type": "array",
                                                  "items": {
                                                    "type": "array",
                                                    "minItems": 2,
                                                    "items": {
                                                      "type": "array",
                                                      "minItems": 2,
                                                      "items": {
                                                        "type": "number"
                                                      }
                                                    }
                                                  }
                                                },
                                                "bbox": {
                                                  "type": "array",
                                                  "minItems": 4,
                                                  "items": {
                                                    "type": "number"
                                                  }
                                                }
                                              }
                                            },
                                            {
                                              "title": "GeoJSON MultiPolygon",
                                              "type": "object",
                                              "required": [
                                                "type",
                                                "coordinates"
                                              ],
                                              "properties": {
                                                "type": {
                                                  "type": "string",
                                                  "enum": [
                                                    "MultiPolygon"
                                                  ]
                                                },
                                                "coordinates": {
                                                  "type": "array",
                                                  "items": {
                                                    "type": "array",
                                                    "items": {
                                                      "type": "array",
                                                      "minItems": 4,
                                                      "items": {
                                                        "type": "array",
                                                        "minItems": 2,
                                                        "items": {
                                                          "type": "number"
                                                        }
                                                      }
                                                    }
                                                  }
                                                },
                                                "bbox": {
                                                  "type": "array",
                                                  "minItems": 4,
                                                  "items": {
                                                    "type": "number"
                                                  }
                                                }
                                              }
                                            }
                                          ]
                                        }
                                      },
                                      "bbox": {
                                        "type": "array",
                                        "minItems": 4,
                                        "items": {
                                          "type": "number"
                                        }
                                      }
                                    }
                                  }
                                ]
                              },
                              "bbox": {
                                "type": "array",
                                "minItems": 4,
                                "items": {
                                  "type": "number"
                                }
                              }
                            }
                          }
                        },
                        "bbox": {
                          "type": "array",
                          "minItems": 4,
                          "items": {
                            "type": "number"
                          }
                        }
                      }
                    }
                  ]
                }
            """;

}
