package com.mraof.minestuck.util;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mraof.minestuck.item.MinestuckItems;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.IRecipeFactory;
import net.minecraftforge.common.crafting.JsonContext;

import java.util.Map;
import java.util.Set;

/**
 * Contains classes for custom recipe types.
 */
public class CraftingRecipes
{
	
	/**
	 * Regular recipes can typically be made independent on if the ingredients are put to the left or the right, as long as the shape remains.
	 * With this class, that possible mirror is not possible, and the recipe will instead only follow the patten exactly.
	 */
	public static class NonMirroredRecipe extends ShapedRecipes
	{
		
		public NonMirroredRecipe(String group, int width, int height, NonNullList<Ingredient> input, ItemStack result)
		{
			super(group, width, height, input, result);
		}
		
		@Override
		public boolean matches(InventoryCrafting inv, World world)
		{
			for (int i = 0; i <= 3 - this.recipeWidth; ++i)
				for (int j = 0; j <= 3 - this.recipeHeight; ++j)
					if (this.checkMatch(inv, i, j))
						return true;
			
			return false;
		}
		
		protected boolean checkMatch(InventoryCrafting inv, int x, int y)
		{
			for (int invX = 0; invX < 3; invX++)
			{
				for (int invY = 0; invY < 3; invY++)
				{
					int posX = invX - x;
					int posY = invY - y;
					Ingredient ingredient = Ingredient.EMPTY;
					
					if (posX >= 0 && posY >= 0 && posX < this.recipeWidth && posY < this.recipeHeight)
					{
						ingredient = this.recipeItems.get(posX + posY * this.recipeWidth);
					}
					
					if (!ingredient.apply(inv.getStackInRowAndColumn(invX, invY)))
					{
						return false;
					}
				}
			}
			
			return true;
		}
		
		public static class Factory extends ShapedFactory
		{
			@Override
			public IRecipe initRecipe(String group, int width, int height, NonNullList<Ingredient> ingredients, ItemStack result)
			{
				return new NonMirroredRecipe(group, width, height, ingredients, result);
			}
		}
	}
	
	/**
	 * Any recipes made out of this instance will not accept captchalogue cards as ingredients, unless said cards are empty and blank.
	 * Beware that this class extends NoMirroredRecipe.
	 */
	public static class EmptyCardRecipe extends NonMirroredRecipe
	{
		
		public EmptyCardRecipe(String group, int width, int height, NonNullList<Ingredient> ingredients, ItemStack result)
		{
			super(group, width, height, ingredients, result);
		}
		
		@Override
		public boolean matches(InventoryCrafting crafting, World world)
		{
			for(int i = 0; i < crafting.getSizeInventory(); i++)
			{
				ItemStack stack = crafting.getStackInSlot(i);
				if(stack.getItem() == MinestuckItems.captchaCard && stack.hasTagCompound() && stack.getTagCompound().hasKey("contentID"))
					return false;
			}
			return super.matches(crafting, world);
		}
		
		public static class Factory extends ShapedFactory
		{
			@Override
			public IRecipe initRecipe(String group, int width, int height, NonNullList<Ingredient> ingredients, ItemStack result)
			{
				return new EmptyCardRecipe(group, width, height, ingredients, result);
			}
		}
	}
	
	public static abstract class ShapedFactory implements IRecipeFactory
	{
		@Override
		public IRecipe parse(JsonContext context, JsonObject json)
		{
			String group = JsonUtils.getString(json, "group", "");
			//Copy-pasted from the ShapedRecipes parser
			Map<Character, Ingredient> ingMap = Maps.newHashMap();
			for (Map.Entry<String, JsonElement> entry : JsonUtils.getJsonObject(json, "key").entrySet())
			{
				if (entry.getKey().length() != 1)
					throw new JsonSyntaxException("Invalid key entry: '" + entry.getKey() + "' is an invalid symbol (must be 1 character only).");
				if (" ".equals(entry.getKey()))
					throw new JsonSyntaxException("Invalid key entry: ' ' is a reserved symbol.");
				
				ingMap.put(entry.getKey().toCharArray()[0], CraftingHelper.getIngredient(entry.getValue(), context));
			}
			ingMap.put(' ', Ingredient.EMPTY);
			
			JsonArray patternJ = JsonUtils.getJsonArray(json, "pattern");
			
			if (patternJ.size() == 0)
				throw new JsonSyntaxException("Invalid pattern: empty pattern not allowed");
			if (patternJ.size() > 3)
				throw new JsonSyntaxException("Invalid pattern: too many rows, 3 is maximum");
			
			String[] pattern = new String[patternJ.size()];
			for (int x = 0; x < pattern.length; ++x)
			{
				String line = JsonUtils.getString(patternJ.get(x), "pattern[" + x + "]");
				if (line.length() > 3)
					throw new JsonSyntaxException("Invalid pattern: too many columns, 3 is maximum");
				if (x > 0 && pattern[0].length() != line.length())
					throw new JsonSyntaxException("Invalid pattern: each row must be the same width");
				pattern[x] = line;
			}
			
			NonNullList<Ingredient> input = NonNullList.withSize(pattern[0].length() * pattern.length, Ingredient.EMPTY);
			Set<Character> keys = Sets.newHashSet(ingMap.keySet());
			keys.remove(' ');
			
			int x = 0;
			for (String line : pattern)
			{
				for (char chr : line.toCharArray())
				{
					Ingredient ing = ingMap.get(chr);
					if (ing == null)
						throw new JsonSyntaxException("Pattern references symbol '" + chr + "' but it's not defined in the key");
					input.set(x++, ing);
					keys.remove(chr);
				}
			}
			
			if (!keys.isEmpty())
				throw new JsonSyntaxException("Key defines symbols that aren't used in pattern: " + keys);
			
			ItemStack result = CraftingHelper.getItemStack(JsonUtils.getJsonObject(json, "result"), context);
			return initRecipe(group, pattern[0].length(), pattern.length, input, result);
		}
		
		public abstract IRecipe initRecipe(String group, int width, int height, NonNullList<Ingredient> ingredients, ItemStack result);
	}
}
