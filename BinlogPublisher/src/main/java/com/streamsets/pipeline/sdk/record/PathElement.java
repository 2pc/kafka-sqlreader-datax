package com.streamsets.pipeline.sdk.record;

import com.google.common.base.Preconditions;
import com.streamsets.pipeline.api.impl.Utils;

import java.util.ArrayList;
import java.util.List;

public class PathElement {

  enum Type {ROOT, MAP, LIST }

  private final Type type;
  private final String name;
  private final int idx;

  public static final PathElement ROOT = new PathElement(Type.ROOT, null, 0);

  private PathElement(Type type, String name, int idx) {
    this.type = type;
    this.name = name;
    this.idx = idx;
  }

  public static PathElement createMapElement(String name) {
    return new PathElement(Type.MAP, name, 0);
  }

  public static PathElement createArrayElement(int idx) {
    return new PathElement(Type.LIST, null, idx);
  }

  public Type getType() {
    return type;
  }

  public String getName() {
    return name;
  }

  public int getIndex() {
    return idx;
  }

  @Override
  public String toString() {
    switch (type) {
      case ROOT:
        return "PathElement[type=ROOT]";
      case MAP:
        return Utils.format("PathElement[type=MAP, name='{}']", getName());
      case LIST:
        return Utils.format("PathElement[type=LIST, idx='{}']", getIndex());
      default:
        throw new IllegalStateException();
    }
  }

  public static final String INVALID_FIELD_PATH = "Invalid fieldPath '{}' at char '{}'";

  public static List<PathElement> parse(String fieldPath, boolean add) {
    Preconditions.checkNotNull(fieldPath, "fieldPath cannot be null");
    List<PathElement> elements = new ArrayList<>();
    elements.add(PathElement.ROOT);
    if (!fieldPath.isEmpty()) {
      char chars[] = fieldPath.toCharArray();
      boolean requiresStart = true;
      boolean requiresName = false;
      boolean requiresIndex = false;
      boolean singleQuote = false;
      boolean doubleQuote = false;
      StringBuilder collector = new StringBuilder();
      int pos = 0;
      for (; pos < chars.length; pos++) {
        if (requiresStart) {
          requiresStart = false;
          requiresName = false;
          requiresIndex = false;
          singleQuote = false;
          doubleQuote = false;
          switch (chars[pos]) {
            case '/':
              requiresName = true;
              break;
            case '[':
              requiresIndex = true;
              break;
            default:
              throw new IllegalArgumentException(Utils.format(INVALID_FIELD_PATH, fieldPath, 0));
          }
        } else {
          if (requiresName) {
            switch (chars[pos]) {
              case '\'':
                if(pos == 0 || chars[pos - 1] != '\\') {
                  if(!doubleQuote) {
                    singleQuote = !singleQuote;
                  } else {
                    collector.append(chars[pos]);
                  }
                } else {
                  collector.setLength(collector.length() - 1);
                  collector.append(chars[pos]);
                }
                break;
              case '"':
                if(pos == 0 || chars[pos - 1] != '\\') {
                  if(!singleQuote) {
                    doubleQuote = !doubleQuote;
                  } else {
                    collector.append(chars[pos]);
                  }
                } else {
                  collector.setLength(collector.length() - 1);
                  collector.append(chars[pos]);
                }
                break;
              case '/':
              case '[':
              case ']':
                if(singleQuote || doubleQuote) {
                  collector.append(chars[pos]);
                } else {
                  if (chars.length <= pos + 1) {
                    throw new IllegalArgumentException(Utils.format(INVALID_FIELD_PATH, fieldPath, pos));
                  }
                  if (chars[pos] == chars[pos + 1]) {
                    collector.append(chars[pos]);
                    pos++;
                  } else {
                    elements.add(PathElement.createMapElement(collector.toString()));
                    requiresStart = true;
                    collector.setLength(0);
                    //not very kosher, we need to replay the current char as start of path element
                    pos--;
                  }
                }
                break;
              default:
                collector.append(chars[pos]);
            }
          } else if (requiresIndex) {
            switch (chars[pos]) {
              case '0':
              case '1':
              case '2':
              case '3':
              case '4':
              case '5':
              case '6':
              case '7':
              case '8':
              case '9':
              case '*': //wildcard character
                collector.append(chars[pos]);
                break;
              case ']':
                try {
                  int index = 0;
                  String indexString = collector.toString();
                  if(!"*".equals(indexString)) {
                    index = Integer.parseInt(indexString);
                  }
                  if (index >= 0) {
                    elements.add(PathElement.createArrayElement(index));
                    requiresStart = true;
                    collector.setLength(0);
                  } else {
                    throw new IllegalArgumentException(Utils.format(INVALID_FIELD_PATH, fieldPath, pos));
                  }
                } catch (NumberFormatException ex) {
                  throw new IllegalArgumentException(Utils.format(INVALID_FIELD_PATH, fieldPath, pos) + ", " +
                    ex.toString(), ex);
                }
                break;
              default:
                throw new IllegalArgumentException(Utils.format(INVALID_FIELD_PATH, fieldPath, pos));
            }
          }
        }
      }

      if(singleQuote || doubleQuote) {
        //If there is no matching quote
        throw new IllegalArgumentException(Utils.format(INVALID_FIELD_PATH, fieldPath, 0));
      } else if (pos < chars.length) {
        throw new IllegalArgumentException(Utils.format(INVALID_FIELD_PATH, fieldPath, pos));
      } else if (collector.length() > 0) {
        // the last path element was a map entry, we need to create it.
        elements.add(PathElement.createMapElement(collector.toString()));
      }
    }
    return elements;
  }
}
